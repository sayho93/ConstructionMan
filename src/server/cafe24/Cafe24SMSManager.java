package server.cafe24;

import delayed.QueuedProcessorImpl;
import delayed.managers.SMSManager;

public class Cafe24SMSManager extends QueuedProcessorImpl {

    private static Cafe24SMSManager instance;
    private Cafe24SMS smsService;

    public static Cafe24SMSManager initialize(Cafe24SMS smsService){
        if(instance == null) instance = new Cafe24SMSManager();
        instance.smsService = smsService;
        return instance;
    }

    public static Cafe24SMSManager getInstanceIfExisting() throws NullPointerException{
        if(instance == null) throw new NullPointerException(Cafe24SMSManager.class.getClass().getSimpleName() + " has not been initialize.");
        return instance;
    }

    public void send(String to, String message){
        this.offer(() -> smsService.sendSMS(Cafe24SMS.toValidNumber(to), message));
    }

}
