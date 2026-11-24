# Tree-shake unused library classes and optimize bytecode without obfuscating names
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
-keep class com.alisamilaytekin.spectras.** { *; }
