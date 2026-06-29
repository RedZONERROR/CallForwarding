package com.git.callforwarding;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class CallForwardingHelper {
    private static final String TAG = "CallForwardingHelper";

    // Carrier Profiles
    public static final int CARRIER_GSM = 0;
    public static final int CARRIER_CDMA = 1;
    public static final int CARRIER_CUSTOM = 2;

    // Forwarding Triggers
    public static final int TRIGGER_UNCONDITIONAL = 0;
    public static final int TRIGGER_BUSY = 1;
    public static final int TRIGGER_UNANSWERED = 2;
    public static final int TRIGGER_UNREACHABLE = 3;

    /**
     * Generate the activation MMI/USSD code.
     */
    public static String getActivationCode(int carrierType, int trigger, String phoneNumber, 
                                           String customPrefix, String customSuffix) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return "";
        }
        
        phoneNumber = phoneNumber.replaceAll("[^+\\d]", ""); // Sanitize number (keep + and digits)

        if (carrierType == CARRIER_GSM) {
            String prefix;
            switch (trigger) {
                case TRIGGER_BUSY:
                    prefix = "*67*";
                    break;
                case TRIGGER_UNANSWERED:
                    prefix = "*61*";
                    break;
                case TRIGGER_UNREACHABLE:
                    prefix = "*62*";
                    break;
                case TRIGGER_UNCONDITIONAL:
                default:
                    prefix = "*21*";
                    break;
            }
            return prefix + phoneNumber + "#";
        } else if (carrierType == CARRIER_CDMA) {
            // CDMA uses *72 for always forward, *71 for conditional forward (busy/no answer)
            String prefix = (trigger == TRIGGER_UNCONDITIONAL) ? "*72" : "*71";
            return prefix + phoneNumber;
        } else {
            // Custom profile
            String prefix = (customPrefix != null) ? customPrefix : "*21*";
            String suffix = (customSuffix != null) ? customSuffix : "#";
            return prefix + phoneNumber + suffix;
        }
    }

    /**
     * Generate the deactivation MMI/USSD code.
     */
    public static String getDeactivationCode(int carrierType, int trigger, String customDeactivationCode) {
        if (carrierType == CARRIER_GSM) {
            switch (trigger) {
                case TRIGGER_BUSY:
                    return "##67#";
                case TRIGGER_UNANSWERED:
                    return "##61#";
                case TRIGGER_UNREACHABLE:
                    return "##62#";
                case TRIGGER_UNCONDITIONAL:
                default:
                    return "##21#";
            }
        } else if (carrierType == CARRIER_CDMA) {
            // CDMA deactivation code is *73
            return "*73";
        } else {
            // Custom deactivation code
            return (customDeactivationCode != null && !customDeactivationCode.trim().isEmpty()) 
                    ? customDeactivationCode : "##21#";
        }
    }

    /**
     * Retrieve the list of active SIM card subscriptions on the device.
     */
    public static List<SubscriptionInfo> getActiveSubscriptions(Context context) {
        List<SubscriptionInfo> subscriptions = new ArrayList<>();
        try {
            SubscriptionManager subscriptionManager = (SubscriptionManager) 
                    context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (subscriptionManager != null) {
                // READ_PHONE_STATE permission must be checked before calling this
                List<SubscriptionInfo> activeList = subscriptionManager.getActiveSubscriptionInfoList();
                if (activeList != null) {
                    subscriptions.addAll(activeList);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for reading active subscriptions: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving active subscriptions: " + e.getMessage());
        }
        return subscriptions;
    }

    /**
     * Get the PhoneAccountHandle corresponding to a SubscriptionInfo.
     */
    public static PhoneAccountHandle getPhoneAccountHandleForSubscription(Context context, SubscriptionInfo info) {
        if (info == null) return null;
        
        int targetSubId = info.getSubscriptionId();
        TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        
        if (telecomManager == null || telephonyManager == null) return null;

        try {
            List<PhoneAccountHandle> phoneAccountHandles = telecomManager.getCallCapablePhoneAccounts();
            
            // Try 1: Android 11 (API 30) and above official mapping API
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                for (PhoneAccountHandle handle : phoneAccountHandles) {
                    try {
                        int subId = telephonyManager.getSubscriptionId(handle);
                        if (subId == targetSubId) {
                            return handle;
                        }
                    } catch (SecurityException ignored) {}
                }
            }

            // Try 2: Fallback to string matching on getId() (standard AOSP stores subId string in getId())
            String targetSubIdStr = String.valueOf(targetSubId);
            for (PhoneAccountHandle handle : phoneAccountHandles) {
                if (handle.getId() != null && handle.getId().equals(targetSubIdStr)) {
                    return handle;
                }
            }

            // Try 3: Fallback matching by SIM Slot Index mapping to index of handles
            int slotIndex = info.getSimSlotIndex();
            if (slotIndex >= 0 && slotIndex < phoneAccountHandles.size()) {
                return phoneAccountHandles.get(slotIndex);
            }

            // Try 4: Return first handle if list is not empty
            if (!phoneAccountHandles.isEmpty()) {
                return phoneAccountHandles.get(0);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while mapping subscription: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error mapping subscription to PhoneAccountHandle: " + e.getMessage());
        }
        
        return null;
    }

    /**
     * Execute the given MMI/USSD code.
     */
    public static boolean executeMmiCode(Context context, String code, PhoneAccountHandle simHandle) {
        if (code == null || code.trim().isEmpty()) {
            return false;
        }

        try {
            // Encode the '#' character to '%23' for dialing intent
            String dialNumber = code.replace("#", "%23");
            Uri uri = Uri.parse("tel:" + dialNumber);
            
            Intent intent = new Intent(Intent.ACTION_CALL, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // Route call to specific SIM if handle is provided
            if (simHandle != null) {
                intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, simHandle);
                Log.d(TAG, "Routing MMI call via SIM: " + simHandle.getId());
            }
            
            context.startActivity(intent);
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "CALL_PHONE permission is missing: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error executing MMI code: " + e.getMessage());
            return false;
        }
    }
}
