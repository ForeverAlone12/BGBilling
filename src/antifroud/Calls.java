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
        this.cotract_id = contract_id;
        this.categories = categories;
        this.session_time = session_time;
    }
    /**
     * Номер договора
     */
    private int cotract_id;
    /**
     * Категории звонка
     */
    private String categories;

    /**
     * Продолжительность звонка
     */
    private int session_time;
}
