+================================================
+
+ Titanium / Furkan - Vectric (Aspire / VCarve Pro) machine output config
+
+ AlphaCAM postunun calisma prensibi ile yeniden yazildi:
+   - Spindle programin basinda M405 ile BIR KEZ baslar.
+     G54 ve G52 Y-2100 de basta bir kez yazilir (HEADER).
+   - AYNI takim ardisik yollarda (NEW_SEGMENT) takim/devir TEKRARLANMAZ;
+     motor durmaz, sadece guvenli hareketlerle devam eder.
+   - Sadece BASKA bir takima gecerken (TOOLCHANGE) once M05, sonra
+     T + G43 H + M03 S yazilir.
+   - ILK takimda M05 yoktur (HEADER icinde, spindle M405 ile yeni baslar).
+   - Takim degisiminde M06 KULLANILMAZ (Titanium kafa/offset ile secer):
+     sadece T[T] ve G43 H[T].
+   - Arklar I/J merkez formatinda -> tam cemberler dogru islenir.
+   - Kanat (FOOTER): G00 Z60, M05, M30.
+   - Satir numarasi ([N]) kullanilmaz (Titanium programlari N icermez).
+
+================================================

POST_NAME = "Titanium Furkan (mm) (*.txt)"

FILE_EXTENSION = "txt"

UNITS = "MM"

+------------------------------------------------
+    Line terminating characters
+------------------------------------------------

LINE_ENDING = "[13][10]"

+------------------------------------------------
+    Block numbering (kullanilmiyor)
+------------------------------------------------

LINE_NUMBER_START     = 0
LINE_NUMBER_INCREMENT = 10
LINE_NUMBER_MAXIMUM = 999999

+================================================
+
+    Formating for variables
+
+================================================

VAR LINE_NUMBER = [N|A|N|1.0]
VAR SPINDLE_SPEED = [S|A|S|1.0]
VAR FEED_RATE = [F|C|F|1.1]
VAR X_POSITION = [X|A|X|1.3]
VAR Y_POSITION = [Y|A|Y|1.3]
VAR Z_POSITION = [Z|A|Z|1.3]
VAR ARC_CENTRE_I_INC_POSITION = [I|A|I|1.3]
VAR ARC_CENTRE_J_INC_POSITION = [J|A|J|1.3]
VAR X_HOME_POSITION = [XH|A|X|1.3]
VAR Y_HOME_POSITION = [YH|A|Y|1.3]
VAR Z_HOME_POSITION = [ZH|A|Z|1.3]
VAR SAFE_Z_HEIGHT = [SAFEZ|A|Z|1.3]

+================================================
+
+    Block definitions for toolpath output
+
+================================================

+---------------------------------------------------
+  Commands output at the start of the file (ILK TAKIM)
+  M405 + G54 + G52 basta bir kez. Ilk takimda M05 YOK.
+---------------------------------------------------

begin HEADER

"( [TOOLPATH_NAME] )"
"#2000 = 2800"
"#2001 = 2100"
"#2002 = 18"
"M405"
"G54"
"G52 Y-2100"
"T[T]"
"G43 H[T]"
"M03 [S]"

+---------------------------------------------------
+  Commands output for rapid moves
+---------------------------------------------------

begin RAPID_MOVE

"G0 [X] [Y] [Z]"

+---------------------------------------------------
+  Commands output for the first feed rate move
+---------------------------------------------------

begin FIRST_FEED_MOVE

"G1 [X] [Y] [Z] [F]"

+---------------------------------------------------
+  Commands output for feed rate moves
+---------------------------------------------------

begin FEED_MOVE

"G1 [X] [Y] [Z]"

+---------------------------------------------------
+  First clockwise arc move (I/J merkez formati)
+---------------------------------------------------

begin FIRST_CW_ARC_MOVE

"G2 [X] [Y] [I] [J] [F]"

+---------------------------------------------------
+  Clockwise arc move
+---------------------------------------------------

begin CW_ARC_MOVE

"G2 [X] [Y] [I] [J]"

+---------------------------------------------------
+  First counterclockwise arc move
+---------------------------------------------------

begin FIRST_CCW_ARC_MOVE

"G3 [X] [Y] [I] [J] [F]"

+---------------------------------------------------
+  Counterclockwise arc move
+---------------------------------------------------

begin CCW_ARC_MOVE

"G3 [X] [Y] [I] [J]"

+---------------------------------------------------
+  Commands output at toolchange (BASKA TAKIMA GECIS)
+  Once M05 (spindle dur), sonra takim + offset + devir.
+  M06 KULLANILMAZ.
+---------------------------------------------------

begin TOOLCHANGE

"M05"
"T[T]"
"G43 H[T]"
"M03 [S]"

+---------------------------------------------------
+  New segment - AYNI takim, farkli yol/feed
+  Ayni takimda HICBIR SEY yazilmaz (spindle durmaz/yeniden baslamaz,
+  T/G43/devir tekrarlanmaz). Blok bilerek bostur.
+---------------------------------------------------

begin NEW_SEGMENT


+---------------------------------------------------
+  Commands output at the end of the file
+---------------------------------------------------

begin FOOTER

"G00 Z60.000"
"M05"
"M30"
