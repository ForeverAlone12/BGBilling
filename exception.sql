-- phpMyAdmin SQL Dump
-- version 4.7.7
-- https://www.phpmyadmin.net/
--
-- Хост: 127.0.0.1
-- Время создания: Апр 01 2018 г., 05:21
-- Версия сервера: 10.1.30-MariaDB
-- Версия PHP: 7.2.2

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
-- Структура таблицы `exception`
--
DROP TABLE IF EXISTS `exception`;
CREATE TABLE `exception` (
  `id` int(11) NOT NULL COMMENT 'код записи',
  `cid` int(11) NOT NULL COMMENT 'код договора'
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='Исключения блокировки';

--
-- Индексы сохранённых таблиц
--

--
-- Индексы таблицы `exception`
--
ALTER TABLE `exception`
  ADD PRIMARY KEY (`id`);

--
-- AUTO_INCREMENT для сохранённых таблиц
--

--
-- AUTO_INCREMENT для таблицы `exception`
--
ALTER TABLE `exception`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT COMMENT 'код записи';
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
