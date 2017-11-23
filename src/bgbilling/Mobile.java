package bgbilling;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvBeanReader;
import org.supercsv.io.ICsvBeanReader;
import org.supercsv.prefs.CsvPreference;

/**
 *
 * @author
 */
public class Mobile {

    /**
     *
     */
    private static List<MobileNumber> prefixMobile = new ArrayList<>();

    /**
     * Чтение данных о мобильных номерах Иркутской области
     */
    public static void readMobileInfo() {
        String filePath = "mobileIrkutsk.csv";

        System.out.println();
        FileReader fileRider;
        try {
            fileRider = new FileReader(filePath);
            ICsvBeanReader csvReader = new CsvBeanReader(fileRider, CsvPreference.STANDARD_PREFERENCE);

            String[] mapping = new String[]{"code", "startNumber", "endNumber"};

            CellProcessor[] proc = getData();

            MobileNumber numb;

            while ((numb = csvReader.read(MobileNumber.class, mapping, proc)) != null) {
                prefixMobile.add(numb);
            }

            csvReader.close();

        } catch (FileNotFoundException ex) {
            System.out.println("Файл не найден");
        } catch (IOException ex) {
            System.out.println("Ошибка при работе с файлом");
        }

    }

    /**
     * Обработка данных из файла
     *
     * @return
     */
    private static CellProcessor[] getData() {
        return new CellProcessor[]{
            new Optional(),
            new NotNull(),
            new NotNull()
        };
    }

    /**
     * Принадлежит ли сотовый телефон операторам Иркутской области
     *
     * @param code код оператора
     * @param number номер телефона
     * @return true - принадлежит, false - не принадлежит
     */
    public static boolean isHaveNumber(String code, String number) {

        int numb = Integer.parseInt(number);

        if (prefixMobile.stream().filter((val) -> (val.getCode().trim().equals(code))).map((val) -> {
            int start = Integer.parseInt(val.getStartNumber().trim());
            int end = Integer.parseInt(val.getEndNumber().trim());
            boolean param2 = (numb >= start && numb <= end);
            return param2;
        }).anyMatch((param2) -> (param2))) {
            return true;
        }
        return false;
    }
}
