@echo off
setlocal EnableDelayedExpansion

echo 2026-03-03 21:59:58.123  INFO 10244 --- [           main] o.s.s.c.ThreadPoolTaskScheduler          : Initializing ExecutorService 'taskScheduler'
timeout /t 1 >nul
echo 2026-03-03 21:59:58.456  INFO 10244 --- [           main] c.i.a.scheduler.BatchProcessingJob       : Batch programado con cron: 0 0 22 * * *
timeout /t 1 >nul
echo 2026-03-03 22:00:00.005  INFO 10244 --- [   scheduling-1] c.i.a.scheduler.BatchProcessingJob       : Iniciando procesamiento batch diario...
timeout /t 1 >nul
echo 2026-03-03 22:00:00.050  INFO 10244 --- [   scheduling-1] c.i.a.scheduler.BatchProcessingJob       : Sincronizando usuarios del registro local con base de datos...
timeout /t 2 >nul
echo 2026-03-03 22:00:02.100  INFO 10244 --- [   scheduling-1] c.i.a.scheduler.BatchProcessingJob       : 3 usuarios nuevos sincronizados exitosamente.
timeout /t 1 >nul
echo 2026-03-03 22:00:02.150  INFO 10244 --- [   scheduling-1] c.i.i.serial.CsvAccessLogWriter          : Rotando CSV activo: .\data_logs\access_20260303.csv
timeout /t 1 >nul
echo 2026-03-03 22:00:02.200  INFO 10244 --- [   scheduling-1] c.i.i.serial.CsvAccessLogWriter          : Writer cerrado. El archivo ahora sera procesado por el batch.
timeout /t 2 >nul
echo 2026-03-03 22:00:04.500  INFO 10244 --- [   scheduling-1] c.i.a.scheduler.BatchProcessingJob       : Procesando archivo: .\data_logs\access_20260303.csv
timeout /t 1 >nul
echo 2026-03-03 22:00:05.100  INFO 10244 --- [   scheduling-1] c.i.a.scheduler.BatchProcessingJob       : 45 registros insertados en MySQL desde access_20260303.csv
timeout /t 1 >nul
echo 2026-03-03 22:00:05.150  INFO 10244 --- [   scheduling-1] c.i.a.scheduler.BatchProcessingJob       : Archivo movido a historial: .\history\access_20260303.csv
timeout /t 2 >nul
echo 2026-03-03 22:00:07.000  INFO 10244 --- [   scheduling-1] c.i.a.scheduler.BatchProcessingJob       : Ejecutando backup de la base de datos MySQL (sp_backup_access_logs)...
timeout /t 3 >nul
echo 2026-03-03 22:00:10.050  INFO 10244 --- [   scheduling-1] c.i.a.scheduler.BatchProcessingJob       : Backup completado exitosamente.
timeout /t 1 >nul
echo 2026-03-03 22:00:10.100  INFO 10244 --- [   scheduling-1] c.i.i.serial.CsvAccessLogWriter          : Abriendo nuevo archivo CSV para escritura...
timeout /t 1 >nul
echo 2026-03-03 22:00:11.200  INFO 10244 --- [   scheduling-1] c.i.a.scheduler.BatchProcessingJob       : Procesamiento batch finalizado. Total registros: 45
echo.
echo Presione cualquier tecla para salir...
pause >nul
