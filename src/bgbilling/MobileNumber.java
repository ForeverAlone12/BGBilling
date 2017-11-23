package bgbilling;


/**
 *
 * @author
 */
public class MobileNumber {

    /**
     * Код мобильного оператора
     */
    private String code;

    /**
     * Начало диапозона номеров
     */
    private String startNumber;

    /**
     * Конец диапозона номеров
     */
    private String endNumber;

    /**
     * Получить код мобильного оператора
     *
     * @return код мобильного оператора
     */
    public String getCode() {
        return code;
    }

    /**
     * Установить код мобильного оператора
     *
     * @param code код мобильного оператора
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Получить начало диапозона номеров
     *
     * @return начало диапозона номеров
     */
    public String getStartNumber() {
        return startNumber;
    }

    /**
     * *
     * Установить начало диапозона номеров
     *
     * @param startNumber начало диапозона номеров
     */
    public void setStartNumber(String startNumber) {
        this.startNumber = startNumber;
    }

    /**
     * Получить конец диапозона номеров
     *
     * @return конец диапозона номеров
     */
    public String getEndNumber() {
        return endNumber;
    }

    /**
     * Установить конец диапозона номеров
     *
     * @param endNumber конец диапозона номеров
     */
    public void setEndNumber(String endNumber) {
        this.endNumber = endNumber;
    }

    
}
