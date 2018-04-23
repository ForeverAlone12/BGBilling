package antifroud;

/**
 * Информация о совершенных звонках абонента
 *
 * @author
 */
public class Calls {

    /**
     * Данные о звонках
     *
     * @param contract_id номер контракта
     * @param categories категория звонка
     * @param session_time продолжительность звонка
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

    /**
     * Получение id контракта
     *
     * @return id контракта
     */
    public int getContarct_id() {
        return contract_id;
    }

    /**
     * Получение времени разговора
     *
     * @return время разговора
     */
    public int getTime() {
        return session_time;
    }

    /**
     * Получение категории звонка Категории звонков: 
     * 1) Международные - звонки     * за пределы РФ. В этом случае набранный 
     * номер будет начинаться с 8... 
     * 2) Междугородние - звонок за пределы Иркутской области по России, включая
     * сотовые телефоны других регионов. 
     * 3) Внутризоновые - звонок в пределах области, но не в пределах одного
     * города. Звонки на сотовые телефоны Иркутской области тоже считаются 
     * внутризоновыми.
     *
     * @return '1' - Международный звонок; '2' - Междугородний звонок; '3' -
     * Внутризоновый звонок.
     */
    public String getCategories() {
        return categories;
    }
}
