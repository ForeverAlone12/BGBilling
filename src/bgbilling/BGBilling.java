package bgbilling;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 *
 * @author
 */
public class BGBilling {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Timer timer = new Timer();
        TimerTask timerTask = new Antifrod();
        timer.schedule(timerTask, 0, 5000);    

    }

}
