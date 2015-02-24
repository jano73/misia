package com.monzano.hardware.rfid;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.monzano.hardware.rfid.model.Dispenser;
import com.monzano.hardware.rfid.model.MockDispenser;
import com.monzano.hardware.rfid.model.RS485Dispenser;
import com.monzano.hardware.uart.HardwareUart;
import com.monzano.hardware.uart.Uart;
import com.monzano.hardware.uart.UartChannel;
import com.monzano.hardware.uart.UartController;

import-export

/**
 * Interface to a group of dispensers.
 * 
 * @author Steve Reckamp
 *
 */
public class DispenserInterface {
    private static Logger log = Logger.getLogger(DispenserInterface.class.getName());
    private static DispenserInterface instance = null;
    private static Set<Integer> dispensedPositions = new HashSet<>();
    private static boolean inSession = false;

    /**
     * Initialized the dispensers using the supplied devicePath.
     * 
     * @param devicePath the path to the serial port e.g. /dev/ttyUSB0
     * @return the dispenser interface generated.
     * @throws IOException on communication errors.
     * @throws IllegalStateException if the serial port is already initialized.
     */
    public static DispenserInterface initialize(String devicePathNet, String devicePathAdam) throws IOException {
        if(instance != null)
            throw new IllegalStateException("Instance already initialized.");
        log.fine(String.format("Initializing Dispenser Interface using '%s' and Adam 4052 using '%s'", devicePathNet, devicePathAdam));
        instance = new DispenserInterface(new HardwareUart(devicePathNet), new HardwareUart(devicePathAdam));
        return instance;
    }

    /**
     * @return the active set of dispensers.
     * @throws IllegalStateException if the serial port is not initialized.
     */
    private static DispenserInterface getInstance() {
        if(instance == null)
            throw new IllegalStateException("Instance needs to be initialized before it can be used.");
        return instance;
    }

    private final Uart uartNet;
    private final UartChannel channelNet;
    private final Uart uartAdam;
    private final UartChannel channelAdam;    
    private Map<Integer, Dispenser> dispensers;

    protected DispenserInterface(Uart uartNet, Uart uartAdam) throws IOException {
        this.uartNet = uartNet;
        uartNet.setBaudRate(UartController.BaudRates._9600);
        uartNet.setFlowControl(UartController.FlowControl.NONE);
        uartNet.setDataBits(UartController.DataBits._8);
        uartNet.setParity(UartController.Parity.NONE);
        uartNet.setStopBits(UartController.StopBits._1);
        uartNet.setTimeout(100);
        channelNet = UartChannel.open(uartNet);
        this.uartAdam = uartAdam;
        uartAdam.setBaudRate(UartController.BaudRates._9600);
        uartAdam.setFlowControl(UartController.FlowControl.NONE);
        uartAdam.setDataBits(UartController.DataBits._8);
        uartAdam.setParity(UartController.Parity.NONE);
        uartAdam.setStopBits(UartController.StopBits._1);
        uartAdam.setTimeout(100);
        channelAdam = UartChannel.open(uartAdam);
        
    }

    protected Uart getUartNet() { return uartNet; }
    
    protected Uart getUartAdam() { return uartAdam; }

    /**
     * Check which positions have active dispensers on them.
     * 
     * @return A {@link Set} containing the positions of active dispensers.
     */
    public static Set<Integer> scan() {
        log.info("Scan");
        getInstance().dispensers = RS485Dispenser.scan(getInstance().channelNet, getInstance().channelAdam, 64);
        if(getInstance().dispensers.size() > 0) {
            log.info(String.format("Found %d dispensers.", getInstance().dispensers.size()));
            return getInstance().dispensers.keySet();
        }
        return new HashSet<Integer>();
    }

    public static Set<Integer> scanFast() {
        log.info("Scan");
        getInstance().dispensers = RS485Dispenser.scanFast(getInstance().channelNet, getInstance().channelAdam, 64);
        if(getInstance().dispensers.size() > 0) {
            log.info(String.format("Found %d dispensers.", getInstance().dispensers.size()));
            return getInstance().dispensers.keySet();
        }
        return new HashSet<Integer>();
    }
    
    
    /**
     * Scans which positions have active dispensers.  If there are not as many
     * as are requested, the rest are created as {@link MockDispenser} starting
     * from address 0 and filling in any empty positions where there are not 
     * active dispensers.
     * 
     * @param count The total number of active/mock dispensers to create.
     * @return A {@link Set} containing the positions of active/mock dispensers.
     */
    public static Set<Integer> createMockDispensers(int count) {
        if(getInstance().dispensers == null) {
            scan();
        }
        if(getInstance().dispensers.size() < count) {
            log.info(String.format("Creating %d mock dispensers.", 
                    count - getInstance().dispensers.size()));
            int idx = 0;
            while(getInstance().dispensers.size() < count) {
                if(!getInstance().dispensers.containsKey(idx)) {
                    log.info(String.format("Creating mock dispenser @ %d.", idx));
                    getInstance().dispensers.put(idx, new MockDispenser());
                }
                idx ++;
            }
        }
        return getInstance().dispensers.keySet();
    }

    /**
     * Starts a session where multiple items are dispensed.
     */
    public static void startSession() {
        if(!inSession) {
            dispensedPositions.clear();
            inSession = true;
        }
    }

    /**
     * Ends the session.  Automatically loads all positions that have been dispensed.
     */
    public static void endSession() {
        for(int idx:dispensedPositions) {
            load(idx);
        }
    }

    /**
     * Dispense an item.
     * 
     * @param position
     * @return The dispensed item's code
     */
    public static long dispense(int position) {
        if(!getInstance().dispensers.containsKey(position)) {
            throw new IllegalArgumentException(String.format("%d is not a valid position.", position));
        }
        final long dispensed = getInstance().dispensers.get(position).dispense();
        dispensedPositions.add(position);
        return dispensed;
    }

    public static void reset(int position) {
        if(!getInstance().dispensers.containsKey(position)) {
            throw new IllegalArgumentException(String.format("%d is not a valid position.", position));
        }
        getInstance().dispensers.get(position).reset();
    }

    public static void motorOn(int position) {
        if(!getInstance().dispensers.containsKey(position)) {
            throw new IllegalArgumentException(String.format("%d is not a valid position.", position));
        }
        getInstance().dispensers.get(position).motorOn();
    }

    public static void motorOff(int position) {
        if(!getInstance().dispensers.containsKey(position)) {
            throw new IllegalArgumentException(String.format("%d is not a valid position.", position));
        }
        getInstance().dispensers.get(position).motorOff();
    }

    /**
     * Load the position.
     * 
     * @param position
     * @return The next item's code
     */
    private static long load(int position) {
        if(!getInstance().dispensers.containsKey(position)) {
            throw new IllegalArgumentException(String.format("%d is not a valid position.", position));
        }
        return getInstance().dispensers.get(position).loadNextItem();
    }
}
