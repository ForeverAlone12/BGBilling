package antifroud;

import java.util.Date;

/**
 *
 * @author
 */
public class Traffic {

    /**
     * 
     */
    private int id;

    /**
     * номер договора
     */
    private int contract_id;

    /**
     * Количество потраченных минут по внутризоновым звонкам
     */
    private int interzone;
    
    /**
     * Количество потраченных минут по городским звонкам
     */
    private int intercity;
    
    /**
     * Количество потраченных минут по международным звонкам
     */
    private int international;
    
    /**
     * День, за который происходят расчеты
     */
    private Date day;
    
    /**
     * Статус договора: 0 - активен, 4 - приостановлен
     */
    private boolean status;



}
