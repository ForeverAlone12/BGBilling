SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET AUTOCOMMIT = 0;
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- База данных: `fraud`
--

-- --------------------------------------------------------

--
-- Структура таблицы `traffic`
--
DROP TABLE IF EXISTS `traffic`;
CREATE TABLE `traffic` (
  `id` int(11) NOT NULL COMMENT 'код записи',
  `cid` int(11) NOT NULL COMMENT 'код договора',
  `interzone` int(11) NOT NULL COMMENT 'время внутризонового времени разговора',
  `intercity` int(11) NOT NULL COMMENT 'время междугороднего разговора',
  `international` int(11) NOT NULL COMMENT 'время международного разговора',
  `day` date NOT NULL COMMENT 'дата разговора',
  `status` tinyint(1) NOT NULL COMMENT 'статус (заблокирован/ не заблокирован)',
  `time1` datetime NOT NULL COMMENT 'начало прошлой выборки',
  `time2` datetime NOT NULL COMMENT 'конец прошлой выборки'
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='Данные о звоонках' COLLATE=utf8_unicode_ci;

--
-- Индексы сохранённых таблиц
--

--
-- Индексы таблицы `traffic`
--
ALTER TABLE `traffic`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `code` (`cid`,`day`,`time1`,`time2`);

--
-- AUTO_INCREMENT для сохранённых таблиц
--

--
-- AUTO_INCREMENT для таблицы `traffic`
--
ALTER TABLE `traffic`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'код записи';
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
