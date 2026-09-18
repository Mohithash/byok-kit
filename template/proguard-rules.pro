# kotlinx.serialization: keep the generated serializers for our @Serializable models.
-keepclassmembers class __PKG__.domain.** {
    *** Companion;
    *** serializer(...);
}
-keepclasseswithmembers class __PKG__.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class __PKG__.domain.**$$serializer { *; }
-dontwarn org.slf4j.**
