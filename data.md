###

1. Comandos de modificación de cantidad para facilitar la decisión de cuántos minerales puede romper un jugador.
2. Modificar el sonido para que se escuche el sonido "correcto" al final y no en cada golpe exitoso.
3. Agregar partículas verdes alrededor del bloque al golpearlo correctamente.
4. Agregar partículas rojas alrededor del bloque al golpearlo incorrectamente.

###

# TRABAJO DE HOY
1. Agregué el sistema de validación de usuario: Solo un jugador puede minar el mismo bloque a la vez
2. Agregué temporizador visual de recolección que se carga desde el JSON de bloques
3. Agregué temporizador visual de respawn del mineral una vez ya fue minado
4. Agregué limitación de minado para que los bloques en estado respawn no puedan romperse
5. Modifiqué el funcionamiento de minado nativo para que la cantidad de golpes necesarios para minar se puedan estimar desde el JSON
6. Agregué el sistema de respawn de minerales para que los bloques no se destruyan sino que se transformen a otro sin mineral por un tiempo
7. Agregué el sistema de límite de recolección de minerales
8. Agregué contadores por tipo de mineral y cantidad total para cada jugador
9. Agregué contadores internos de reinicio de cantidad por jugador para la cantidad de minerales posibles. 5 minutos para el reset en default.
10. Agregué un Dashboard que permite ver los datos de cada mineral y valores totales por jugador
11. 