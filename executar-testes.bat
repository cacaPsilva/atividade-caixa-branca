@echo off
REM Mesmo procedimento do executar-testes.sh, para Windows (JDK 11+ no PATH).
cd /d "%~dp0"
if exist build rmdir /s /q build
mkdir build\original build\revisado build\teste-original build\teste-revisado build\pasta-original
if not exist evidencias mkdir evidencias

javac -encoding UTF-8 -d build\original original\EstoqueArquivo.java || exit /b 1
javac -encoding UTF-8 -d build\teste-original -cp build\original testes\TesteOriginal.java || exit /b 1
javac -encoding UTF-8 -d build\revisado revisado\EstoqueArquivo.java revisado\EstoqueException.java || exit /b 1
javac -encoding UTF-8 -d build\teste-revisado -cp build\revisado testes\TesteRevisado.java || exit /b 1

pushd build\pasta-original
java -Dstdout.encoding=UTF-8 -cp ..\original;..\teste-original TesteOriginal
popd
echo.
java -Dstdout.encoding=UTF-8 -cp build\revisado;build\teste-revisado TesteRevisado
