package antifroud;

/**
 *
 * @author
 */
public class Calls {

    /**
     * 
     * @param contract_id
     * @param categories
     * @param session_time 
     */
    Calls(int contract_id, String categories, int session_time) {
        this.contract_id = contract_id;
        this.categories = categories;
        this.session_time = session_time;
    }
    /**
     * Номер договора
     */
    private int contract_id;
    /**
     * Категории звонка
     */
    private String categories;

    /**
     * Продолжительность звонка
     */
    private int session_time;
    
    public int getContarct_id(){
        return contract_id;
    }
    
    public int getTime(){
        return session_time;
    }
    
    public String getCategories(){
        return categories;
    }
    
}
