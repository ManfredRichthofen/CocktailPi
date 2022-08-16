package net.alex9849.cocktailmaker.service;

import net.alex9849.cocktailmaker.iface.IGpioController;
import net.alex9849.cocktailmaker.iface.IGpioPin;
import net.alex9849.cocktailmaker.model.Pump;
import net.alex9849.cocktailmaker.payload.dto.settings.ReversePumpingSettings;
import net.alex9849.cocktailmaker.repository.OptionsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.util.*;
import java.util.concurrent.*;

@Service
@Transactional
public class PumpUpService {
    @Autowired
    private PumpService pumpService;

    @Autowired
    private OptionsRepository optionsRepository;

    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private IGpioController gpioController;

    private ScheduledFuture<?> automaticPumpBackTask;
    private ReversePumpingSettings.Full reversePumpSettings;
    private final Map<Long, ScheduledFuture<?>> pumpingUpPumpIdsToStopTask = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public void postConstruct() {
        this.reversePumpSettings = this.getReversePumpingSettings();
        this.setReversePumpingDirection(false);
        this.applyReversePumpSettingsAndResetCountdown();
    }

    public synchronized boolean isPumpPumpingUp(Pump pump) {
        return this.pumpingUpPumpIdsToStopTask.containsKey(pump.getId());
    }

    public synchronized void cancelPumpUp(Pump pump) {
        ScheduledFuture<?> pumpUpFuture = this.pumpingUpPumpIdsToStopTask.remove(pump.getId());
        if (pumpUpFuture != null) {
            pump.setRunning(false);
            pumpUpFuture.cancel(false);
            pumpService.updatePump(pump);
        }
        if (this.pumpingUpPumpIdsToStopTask.isEmpty()) {
            this.setReversePumpingDirection(false);
        }
    }

    public synchronized void pumpBackOrUp(Pump pump, boolean pumpUp) {
        pumpBackOrUpInternal(pump, pumpUp, 0);
    }

    private synchronized void pumpBackOrUpInternal(Pump pump, boolean pumpUp, int overShoot) {
        if ((pumpUp == this.isPumpDirectionReversed()) && !this.pumpingUpPumpIdsToStopTask.isEmpty()) {
            throw new IllegalArgumentException("A pump is currently pumping into the other direction!");
        }
        if (this.pumpingUpPumpIdsToStopTask.containsKey(pump.getId())) {
            throw new IllegalArgumentException("Pump is already pumping up/back!");
        }
        if (this.pumpService.getPumpOccupation(pump) != PumpService.PumpOccupation.NONE) {
            throw new IllegalArgumentException("Pump is currently occupied!");
        }
        this.setReversePumpingDirection(!pumpUp);
        if (pumpUp) {
            this.applyReversePumpSettingsAndResetCountdown();
        }
        double overShootMultiplier = 1 + (((double) overShoot) / 100);
        int runTime = (int) (pump.getConvertMlToRuntime(pump.getTubeCapacityInMl()) * overShootMultiplier);
        pump.setRunning(true);
        CountDownLatch cdl = new CountDownLatch(1);
        ScheduledFuture<?> stopTask = scheduler.schedule(() -> {
            pump.setRunning(false);
            pump.setPumpedUp(pumpUp);
            try {
                cdl.await();
                this.cancelPumpUp(pump);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, runTime, TimeUnit.MILLISECONDS);
        this.pumpingUpPumpIdsToStopTask.put(pump.getId(), stopTask);
        cdl.countDown();
        webSocketService.broadcastPumpLayout(pumpService.getAllPumps());
    }

    public synchronized boolean isPumpDirectionReversed() {
        if (this.reversePumpSettings == null || !this.reversePumpSettings.isEnable()) {
            return false;
        }
        for (ReversePumpingSettings.VoltageDirectorPin pin : this.reversePumpSettings.getSettings().getDirectorPins()) {
            IGpioPin gpioPin = gpioController.getGpioPin(pin.getBcmPin());
            boolean reversed = gpioPin.isHigh() != pin.isForwardStateHigh();
            if (reversed) {
                return true;
            }
        }
        return false;
    }

    private synchronized void setReversePumpingDirection(boolean reverse) {
        if (reversePumpSettings == null || !reversePumpSettings.isEnable()) {
            if (reverse) {
                throw new IllegalStateException("Can't change pump direction! Reverse pump settings not defined!");
            } else {
                return;
            }
        }
        for (ReversePumpingSettings.VoltageDirectorPin pinConfig : reversePumpSettings.getSettings().getDirectorPins()) {
            IGpioPin gpioPin = gpioController.getGpioPin(pinConfig.getBcmPin());
            if (reverse != pinConfig.isForwardStateHigh()) {
                gpioPin.setHigh();
            } else {
                gpioPin.setLow();
            }
        }
    }

    public synchronized void applyReversePumpSettingsAndResetCountdown() {
        if (automaticPumpBackTask != null) {
            automaticPumpBackTask.cancel(false);
            automaticPumpBackTask = null;
        }
        if (reversePumpSettings == null || !reversePumpSettings.isEnable()) {
            return;
        }
        if (reversePumpSettings.getSettings().getAutoPumpBackTimer() == 0) {
            return;
        }
        this.setReversePumpingDirection(false);
        long delay = reversePumpSettings.getSettings().getAutoPumpBackTimer();
        automaticPumpBackTask = scheduler.scheduleAtFixedRate(() -> {
            List<Pump> allPumps = pumpService.getAllPumps();
            if (allPumps.stream().anyMatch(p -> pumpService.getPumpOccupation(p) != PumpService.PumpOccupation.NONE)) {
                return;
            }
            for (Pump pump : allPumps) {
                if (pump.isPumpedUp()) {
                    this.pumpBackOrUpInternal(pump, false, reversePumpSettings.getSettings().getOvershoot());
                }
            }
        }, delay, delay, TimeUnit.MINUTES);
    }

    public synchronized void setReversePumpingSettings(ReversePumpingSettings.Full settings) {
        if(pumpService.getAllPumps().stream().anyMatch(p -> pumpService.getPumpOccupation(p) != PumpService.PumpOccupation.NONE)) {
            throw new IllegalStateException("Pumps occupied!");
        }

        optionsRepository.setOption("RPSEnable", Boolean.valueOf(settings.isEnable()).toString());
        if (settings.isEnable()) {
            ReversePumpingSettings.Details details = settings.getSettings();
            List<ReversePumpingSettings.VoltageDirectorPin> vdPins = details.getDirectorPins();
            optionsRepository.setOption("RPSOvershoot", Integer.valueOf(details.getOvershoot()).toString());
            optionsRepository.setOption("RPSAutoPumpBackTimer", Integer.valueOf(details.getAutoPumpBackTimer()).toString());

            Set<Integer> seenPins = new HashSet<>();
            for (int i = 0; i < vdPins.size(); i++) {
                ReversePumpingSettings.VoltageDirectorPin vdPin = vdPins.get(i);
                if(pumpService.findByBcmPin(vdPin.getBcmPin()).isPresent()) {
                    throw new IllegalArgumentException("BCM-Pin is already used by a pump!");
                }
                if(!seenPins.add(vdPin.getBcmPin())) {
                    throw new IllegalArgumentException("BCM-Pins must be different!");
                }

                optionsRepository.setOption("RPSVDPinFwStateHigh" + (i + 1), Boolean.valueOf(vdPin.isForwardStateHigh()).toString());
                optionsRepository.setOption("RPSVDPinBcm" + (i + 1), Integer.valueOf(vdPin.getBcmPin()).toString());
            }
        } else {
            optionsRepository.delOption("RPSOvershoot", false);
            optionsRepository.delOption("RPSAutoPumpBackTimer", false);
            optionsRepository.delOption("RPSVDPinFwStateHigh%", true);
            optionsRepository.delOption("RPSVDPinBcm%", true);
        }
        this.reversePumpSettings = settings;
        this.setReversePumpingDirection(false);
        applyReversePumpSettingsAndResetCountdown();
    }

    public synchronized ReversePumpingSettings.Full getReversePumpingSettings() {
        ReversePumpingSettings.Full settings = new ReversePumpingSettings.Full();
        settings.setEnable(Boolean.parseBoolean(optionsRepository.getOption("RPSEnable")));
        if (settings.isEnable()) {
            ReversePumpingSettings.VoltageDirectorPin vdPin1 = new ReversePumpingSettings.VoltageDirectorPin();
            ReversePumpingSettings.VoltageDirectorPin vdPin2 = new ReversePumpingSettings.VoltageDirectorPin();
            vdPin1.setBcmPin(Integer.parseInt(optionsRepository.getOption("RPSVDPinBcm1")));
            vdPin1.setForwardStateHigh(Boolean.parseBoolean(optionsRepository.getOption("RPSVDPinFwStateHigh1")));
            vdPin2.setBcmPin(Integer.parseInt(optionsRepository.getOption("RPSVDPinBcm2")));
            vdPin2.setForwardStateHigh(Boolean.parseBoolean(optionsRepository.getOption("RPSVDPinFwStateHigh2")));

            ReversePumpingSettings.Details details = new ReversePumpingSettings.Details();
            details.setDirectorPins(Arrays.asList(vdPin1, vdPin2));
            details.setOvershoot(Integer.parseInt(optionsRepository.getOption("RPSOvershoot")));
            details.setAutoPumpBackTimer(Integer.parseInt(optionsRepository.getOption("RPSAutoPumpBackTimer")));
            settings.setSettings(details);
        }
        return settings;
    }

}
