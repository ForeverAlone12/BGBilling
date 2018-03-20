package antifroud;

import java.util.Date;

/**
 *
 * @author
 */
public class Traffic {

    /**
     * 
     * @param id
     * @param contract_id
     * @param interzone
     * @param intercity
     * @param international
     * @param day
     * @param status 
     */
    Traffic(int id, int contract_id, int interzone, int intercity, int international, Date day, boolean status) {
        this.id = id;
        this.contract_id = contract_id;
        this.interzone = interzone;
        this.intercity = intercity;
        this.international = international;
        this.day = day;
        this.status = status;
    }
    
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
