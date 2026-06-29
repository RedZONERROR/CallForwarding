# Proguard rules for Call Forwarding App
# Add project specific Proguard rules here.

# Specify custom obfuscation dictionaries using our confusing letters dictionary
-obfuscationdictionary obfuscation-dictionary.txt
-classobfuscationdictionary obfuscation-dictionary.txt
-packageobfuscationdictionary obfuscation-dictionary.txt

# Preserve ViewBinding members to prevent inflation/binding errors
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(...);
    public static *** bind(...);
}

# General optimization settings
-repackageclasses 'o0o'
-allowaccessmodification

# Keep telephony and carrier classes if needed
-keepclassmembers class * {
    *** *Telephony*;
    *** *Subscription*;
}
