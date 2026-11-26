#!/usr/bin/env bash
# Compila original e revisado separadamente e roda os dois programas de teste.
# Requisito: JDK 11 ou superior (testado com OpenJDK 21).
set -e
cd "$(dirname "$0")"
rm -rf build
mkdir -p build/original build/revisado build/teste-original build/teste-revisado build/pasta-original evidencias

javac -encoding UTF-8 -d build/original original/EstoqueArquivo.java
javac -encoding UTF-8 -d build/teste-original -cp build/original testes/TesteOriginal.java
javac -encoding UTF-8 -d build/revisado revisado/*.java
javac -encoding UTF-8 -d build/teste-revisado -cp build/revisado testes/TesteRevisado.java

# O original usa caminhos fixos na pasta atual, então roda dentro de uma pasta vazia.
(cd build/pasta-original && java -Dstdout.encoding=UTF-8 -cp ../original:../teste-original TesteOriginal) | tee evidencias/saida-testes-original.txt
echo
java -Dstdout.encoding=UTF-8 -cp build/revisado:build/teste-revisado TesteRevisado | tee evidencias/saida-testes-revisado.txt
