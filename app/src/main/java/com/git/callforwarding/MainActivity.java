package com.git.callforwarding;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telecom.PhoneAccountHandle;
import android.telephony.SubscriptionInfo;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.git.callforwarding.databinding.ActivityMainBinding;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "CallForwardingPrefs";
    private static final String KEY_STATUS = "cf_status";
    private static final String KEY_NUMBER = "cf_number";
    private static final String KEY_CARRIER_TYPE = "cf_carrier_type";
    private static final String KEY_TRIGGER = "cf_trigger";
    private static final String KEY_HISTORY = "cf_history";

    private ActivityMainBinding binding;
    private List<SubscriptionInfo> mActiveSubscriptions = new ArrayList<>();
    private List<String> mHistoryList = new ArrayList<>();

    // Permission Launcher
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean callPhoneGranted = result.getOrDefault(Manifest.permission.CALL_PHONE, false);
                Boolean readPhoneStateGranted = result.getOrDefault(Manifest.permission.READ_PHONE_STATE, false);
                
                if (Boolean.TRUE.equals(callPhoneGranted) && Boolean.TRUE.equals(readPhoneStateGranted)) {
                    loadSimCards();
                } else {
                    Toast.makeText(this, "Permissions are required to run call routing codes on your SIM card.", Toast.LENGTH_LONG).show();
                }
            });

    // Contact Picker Launcher
    private final ActivityResultLauncher<Intent> contactPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri contactUri = result.getData().getData();
                    if (contactUri != null) {
                        retrievePhoneNumber(contactUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Adjust top and bottom padding of the root NestedScrollView to match the status/navigation bar insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        initSpinners();
        initCarrierRadioGroup();
        initActionButtons();
        loadSavedState();
        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        String[] permissions = new String[]{
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_PHONE_STATE
        };

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            loadSimCards();
        } else {
            requestPermissionsLauncher.launch(permissions);
        }
    }

    private void loadSimCards() {
        mActiveSubscriptions = CallForwardingHelper.getActiveSubscriptions(this);
        if (mActiveSubscriptions.size() > 1) {
            binding.simSelectionContainer.setVisibility(View.VISIBLE);
            
            List<String> simLabels = new ArrayList<>();
            for (int i = 0; i < mActiveSubscriptions.size(); i++) {
                SubscriptionInfo info = mActiveSubscriptions.get(i);
                CharSequence carrierName = info.getCarrierName();
                String label = "SIM " + (info.getSimSlotIndex() + 1) + ": " + 
                        ((carrierName != null && carrierName.length() > 0) ? carrierName : "Unknown Carrier");
                simLabels.add(label);
            }
            
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
                    android.R.layout.simple_spinner_item, simLabels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            binding.spinnerSimSelect.setAdapter(adapter);
        } else {
            binding.simSelectionContainer.setVisibility(View.GONE);
        }
    }

    private void initSpinners() {
        // Trigger Types Spinner
        String[] triggerTypes = new String[]{
                "Always Forward (Unconditional)",
                "When Busy",
                "When Unanswered",
                "When Unreachable",
                "Clear / Deactivate All"
        };
        
        ArrayAdapter<String> triggerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, triggerTypes);
        triggerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerForwardingType.setAdapter(triggerAdapter);

        binding.spinnerForwardingType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // If "Clear / Deactivate All" is selected, hide the number input since it's not needed.
                if (position == 4) {
                    binding.phoneInputLayout.setVisibility(View.GONE);
                    binding.btnPickContact.setVisibility(View.GONE);
                    binding.btnStartForwarding.setText("Execute Deactivate All");
                } else {
                    binding.phoneInputLayout.setVisibility(View.VISIBLE);
                    binding.btnPickContact.setVisibility(View.VISIBLE);
                    binding.btnStartForwarding.setText("Enable Call Forwarding");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void initCarrierRadioGroup() {
        binding.radioGroupCarrier.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_custom) {
                binding.customCarrierContainer.setVisibility(View.VISIBLE);
            } else {
                binding.customCarrierContainer.setVisibility(View.GONE);
            }
        });
    }

    private void initActionButtons() {
        // Contact picker button
        binding.btnPickContact.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
            contactPickerLauncher.launch(intent);
        });

        // Enable button
        binding.btnStartForwarding.setOnClickListener(v -> {
            executeForwarding(true);
        });

        // Disable button
        binding.btnStopForwarding.setOnClickListener(v -> {
            executeForwarding(false);
        });

        // Clear history button
        binding.btnClearHistory.setOnClickListener(v -> {
            mHistoryList.clear();
            saveHistory();
            updateHistoryUI();
        });
    }

    private void executeForwarding(boolean activate) {
        // Double check permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            checkAndRequestPermissions();
            return;
        }

        int carrierType = getSelectedCarrierType();
        int trigger = binding.spinnerForwardingType.getSelectedItemPosition();
        
        String phoneNumber = "";
        if (trigger != 4 || activate) { // If not clear-all OR we are activating
            phoneNumber = binding.phoneInput.getText().toString().trim();
            if (activate && trigger != 4 && phoneNumber.isEmpty()) {
                binding.phoneInputLayout.setError("Phone number is required");
                return;
            }
            binding.phoneInputLayout.setError(null);
        }

        // Get selected SIM handle and subscription ID
        Integer subId = null;
        PhoneAccountHandle targetSimHandle = null;
        if (mActiveSubscriptions.size() > 1) {
            int selectedIndex = binding.spinnerSimSelect.getSelectedItemPosition();
            if (selectedIndex >= 0 && selectedIndex < mActiveSubscriptions.size()) {
                SubscriptionInfo subInfo = mActiveSubscriptions.get(selectedIndex);
                subId = subInfo.getSubscriptionId();
                targetSimHandle = CallForwardingHelper.getPhoneAccountHandleForSubscription(this, subInfo);
            }
        } else if (mActiveSubscriptions.size() == 1) {
            subId = mActiveSubscriptions.get(0).getSubscriptionId();
            targetSimHandle = CallForwardingHelper.getPhoneAccountHandleForSubscription(this, mActiveSubscriptions.get(0));
        }

        String mmiCode;
        if (activate) {
            if (trigger == 4) {
                // Clear All / Deactivate All
                mmiCode = "##002#";
            } else {
                String customPrefix = binding.customActivationPrefix.getText().toString().trim();
                String customSuffix = binding.customActivationSuffix.getText().toString().trim();
                mmiCode = CallForwardingHelper.getActivationCode(carrierType, trigger, phoneNumber, customPrefix, customSuffix);
            }
        } else {
            String customStop = binding.customDeactivationCode.getText().toString().trim();
            mmiCode = CallForwardingHelper.getDeactivationCode(carrierType, trigger, customStop);
        }

        if (mmiCode.isEmpty()) {
            Toast.makeText(this, "Failed to build forwarding code", Toast.LENGTH_SHORT).show();
            return;
        }

        final String finalPhoneNumber = phoneNumber;
        final String finalMmiCode = mmiCode;
        
        Toast.makeText(this, "Updating call forwarding...", Toast.LENGTH_SHORT).show();

        CallForwardingHelper.executeMmiCode(this, mmiCode, targetSimHandle, subId, new CallForwardingHelper.UssdResultCallback() {
            @Override
            public void onUssdSuccess(String response) {
                if (activate) {
                    if (trigger == 4) {
                        saveRoutingState("Deactivated All", "", carrierType, trigger);
                    } else {
                        saveRoutingState("Routing Enabled", finalPhoneNumber, carrierType, trigger);
                        addToHistory(finalPhoneNumber);
                    }
                } else {
                    saveRoutingState("Routing Deactivated", "", carrierType, trigger);
                }
                Toast.makeText(MainActivity.this, "Success:\n" + response, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onUssdFailure(String errorMessage, boolean canFallback) {
                if (canFallback) {
                    // Update state assuming it runs via dialer fallback
                    if (activate) {
                        if (trigger == 4) {
                            saveRoutingState("Deactivated All", "", carrierType, trigger);
                        } else {
                            saveRoutingState("Routing Enabled", finalPhoneNumber, carrierType, trigger);
                            addToHistory(finalPhoneNumber);
                        }
                    } else {
                        saveRoutingState("Routing Deactivated", "", carrierType, trigger);
                    }
                    Toast.makeText(MainActivity.this, "MMI Dialed: " + finalMmiCode + " (" + errorMessage + ")", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "Failed: " + errorMessage, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private int getSelectedCarrierType() {
        int checkedId = binding.radioGroupCarrier.getCheckedRadioButtonId();
        if (checkedId == R.id.radio_gsm) {
            return CallForwardingHelper.CARRIER_GSM;
        } else if (checkedId == R.id.radio_cdma) {
            return CallForwardingHelper.CARRIER_CDMA;
        } else {
            return CallForwardingHelper.CARRIER_CUSTOM;
        }
    }

    private void retrievePhoneNumber(Uri contactUri) {
        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER};
        try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                String number = cursor.getString(numberIndex);
                binding.phoneInput.setText(number);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to read contact info: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveRoutingState(String status, String number, int carrierType, int trigger) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_STATUS, status)
                .putString(KEY_NUMBER, number)
                .putInt(KEY_CARRIER_TYPE, carrierType)
                .putInt(KEY_TRIGGER, trigger)
                .apply();
                
        updateStatusCard(status, number, trigger);
    }

    private void loadSavedState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String status = prefs.getString(KEY_STATUS, "Routing Status: Unconfigured");
        String number = prefs.getString(KEY_NUMBER, "");
        int carrierType = prefs.getInt(KEY_CARRIER_TYPE, CallForwardingHelper.CARRIER_GSM);
        int trigger = prefs.getInt(KEY_TRIGGER, CallForwardingHelper.TRIGGER_UNCONDITIONAL);
        
        binding.phoneInput.setText(number);
        
        if (carrierType == CallForwardingHelper.CARRIER_GSM) {
            binding.radioGsm.setChecked(true);
        } else if (carrierType == CallForwardingHelper.CARRIER_CDMA) {
            binding.radioCdma.setChecked(true);
        } else {
            binding.radioCustom.setChecked(true);
        }

        binding.spinnerForwardingType.setSelection(trigger);
        updateStatusCard(status, number, trigger);

        // Load History
        String historyJson = prefs.getString(KEY_HISTORY, "[]");
        try {
            JSONArray array = new JSONArray(historyJson);
            mHistoryList.clear();
            for (int i = 0; i < array.length(); i++) {
                mHistoryList.add(array.getString(i));
            }
        } catch (JSONException ignored) {}
        updateHistoryUI();
    }

    private void updateStatusCard(String status, String number, int trigger) {
        binding.statusTitle.setText(status);
        
        if (status.contains("Enabled")) {
            binding.statusIndicator.setBackgroundColor(Color.parseColor("#00E676")); // Neon green dot
            String triggerStr = binding.spinnerForwardingType.getItemAtPosition(trigger).toString();
            binding.statusDetail.setText("Currently forwarding all incoming calls triggered by [" + triggerStr + "] to " + number + ".");
        } else if (status.contains("Deactivated")) {
            binding.statusIndicator.setBackgroundColor(Color.parseColor("#FF3D00")); // Neon red dot
            binding.statusDetail.setText("All call forwarding settings have been requested to deactivate.");
        } else {
            binding.statusIndicator.setBackgroundColor(Color.parseColor("#FFC400")); // Amber dot
            binding.statusDetail.setText("No call forwarding set through this app yet.");
        }
    }

    private void addToHistory(String number) {
        if (number == null || number.trim().isEmpty()) return;
        
        // Remove if exists to push to front
        mHistoryList.remove(number);
        mHistoryList.add(0, number);
        
        // Cap size at 5 items
        if (mHistoryList.size() > 5) {
            mHistoryList.remove(mHistoryList.size() - 1);
        }
        
        saveHistory();
        updateHistoryUI();
    }

    private void saveHistory() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        JSONArray array = new JSONArray(mHistoryList);
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply();
    }

    private void updateHistoryUI() {
        binding.historyContainer.removeAllViews();
        
        if (mHistoryList.isEmpty()) {
            binding.tvEmptyHistory.setVisibility(View.VISIBLE);
            return;
        }
        
        binding.tvEmptyHistory.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        
        for (String number : mHistoryList) {
            View item = inflater.inflate(android.R.layout.simple_list_item_1, binding.historyContainer, false);
            TextView text = item.findViewById(android.R.id.text1);
            text.setText(number);
            text.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            text.setPadding(8, 16, 8, 16);
            
            item.setOnClickListener(v -> {
                binding.phoneInput.setText(number);
                binding.phoneInput.setSelection(number.length());
                Toast.makeText(this, "Selected: " + number, Toast.LENGTH_SHORT).show();
            });
            
            binding.historyContainer.addView(item);
            
            // Add custom separator view
            View separator = new View(this);
            separator.setLayoutParams(new RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT, 2));
            separator.setBackgroundColor(ContextCompat.getColor(this, R.color.divider));
            binding.historyContainer.addView(separator);
        }
    }
}
