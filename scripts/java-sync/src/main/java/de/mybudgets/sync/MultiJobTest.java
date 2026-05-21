package de.mybudgets.sync;

import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.callback.AbstractHBCICallback;
import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.HBCIUtils;
import org.kapott.hbci.passport.AbstractHBCIPassport;
import org.kapott.hbci.structures.Konto;
import org.kapott.hbci.structures.Value;
import org.kapott.hbci.status.HBCIExecStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Multi-Job Dialog Test für BBBank
 * 
 * Testet den Multi-Job-Ansatz wie Hibiscus:
 * - Mehrere KUmsAllCamt-Jobs mit unterschiedlichen Date-Windows
 * - Alle Jobs in EINEN Dialog (addJob mehrfach)
 * - EIN execute() → EINE TAN für alle!
 * 
 * Ziel: Mehr als 150 TX pro TAN abrufen
 */
public class MultiJobTest {

    private static final String TAG = "MultiJobTest";
    private static final String VERSION = "1.0.7"; // Fixed flatData parsing (ArrayList vs Object[])

    public static void main(String[] args) {
        try {
            // Load configuration
            Properties props = loadConfig();
            String iban = props.getProperty("iban");
            String userId = props.getProperty("userId");
            String pin = props.getProperty("pin");
            String blz = props.getProperty("blz");
            String bic = props.getProperty("bic", ""); // Optional, but recommended for CAMT
            String tanMethod = props.getProperty("tanMethod", "");
            boolean debug = Boolean.parseBoolean(props.getProperty("debug", "false"));
            
            // Multi-Job specific config
            int maxChunks = Integer.parseInt(props.getProperty("multiJob.maxChunks", "3"));
            int yearsPerChunk = Integer.parseInt(props.getProperty("multiJob.yearsPerChunk", "1"));
            boolean useEnddate = Boolean.parseBoolean(props.getProperty("multiJob.useEnddate", "false"));

            // Check required fields
            if (iban == null || userId == null || pin == null) {
                System.err.println("Fehler: iban, userId, pin sind Pflichtfelder in config.properties");
                System.exit(2);
            }

            // Derive BLZ from IBAN if not provided
            if (blz == null || blz.isEmpty()) {
                blz = blzFromIban(iban);
            }

            System.out.println("╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║        Multi-Job Dialog Test (BBBank KUmsAllCamt)             ║");
            System.out.println("║        VERSION: " + VERSION + "                                          ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("IBAN: " + maskIban(iban));
            System.out.println("BLZ: " + blz);
            System.out.println("BIC: " + (bic.isEmpty() ? "(not set)" : bic));
            System.out.println("User: " + maskUser(userId));
            System.out.println("TAN-Methode: " + (tanMethod.isEmpty() ? "(auto)" : tanMethod));
            System.out.println();
            System.out.println("Multi-Job Config:");
            System.out.println("  maxChunks: " + maxChunks);
            System.out.println("  yearsPerChunk: " + yearsPerChunk);
            System.out.println("  useEnddate: " + useEnddate);
            System.out.println();

            // Initialize HBCI
            initHbci(debug, userId, pin, blz);

            // Setup passport directory
            File passportDir = new File("scripts/java-sync/passports");
            passportDir.mkdirs();
            File passportFile = new File(passportDir, "passport_" + blz + "_multi.dat");

            HBCIUtils.setParam("client.passport.PinTan.filename", passportFile.getAbsolutePath());
            HBCIUtils.setParam("client.passport.PinTan.init", "1");
            HBCIUtils.setParam("client.passport.default", "PinTan");
            
            // Force correct host/port (prevents issues with cached passport)
            HBCIUtils.setParam("client.passport.PinTan.checkcert", "1");

            // Create passport
            AbstractHBCIPassport passport = (AbstractHBCIPassport) AbstractHBCIPassport.getInstance("PinTan");

            // Set fields via reflection
            try {
                java.lang.reflect.Method setCountry = passport.getClass().getMethod("setCountry", String.class);
                setCountry.invoke(passport, "DE");
            } catch (Exception e) {
                System.err.println("Could not set country: " + e.getMessage());
            }
            
            try {
                java.lang.reflect.Method setHost = passport.getClass().getMethod("setHost", String.class);
                setHost.invoke(passport, "fints2.atruvia.de/cgi-bin/hbciservlet");
                System.out.println("Set host to fints2.atruvia.de/cgi-bin/hbciservlet");
            } catch (Exception e) {
                System.err.println("Could not set host: " + e.getMessage());
            }
            
            try {
                java.lang.reflect.Method setPort = passport.getClass().getMethod("setPort", Integer.class);
                setPort.invoke(passport, 443);
                System.out.println("Set port to 443");
            } catch (Exception e) {
                System.err.println("Could not set port: " + e.getMessage());
            }
            
            try {
                java.lang.reflect.Method setFilterType = passport.getClass().getMethod("setFilterType", String.class);
                setFilterType.invoke(passport, "Base64");
            } catch (Exception e) {
                System.err.println("Could not set filterType: " + e.getMessage());
            }

            try {
                java.lang.reflect.Method setUserId = passport.getClass().getMethod("setUserId", String.class);
                setUserId.invoke(passport, userId);
            } catch (Exception e) {
                System.err.println("Could not set userId: " + e.getMessage());
            }

            try {
                java.lang.reflect.Method setCustomerId = passport.getClass().getMethod("setCustomerId", String.class);
                setCustomerId.invoke(passport, userId);
            } catch (Exception e) {
                System.err.println("Could not set customerId: " + e.getMessage());
            }

            // Don't set tanMethod - let callback handle TAN-method selection
            if (!tanMethod.isEmpty() && !tanMethod.equals("(auto)")) {
                System.out.println("Using TAN method: " + tanMethod);
                HBCIUtils.setParam("client.passport.PinTan.tanmethod", tanMethod);
            } else {
                System.out.println("TAN method: Auto-select via callback");
            }

            // Create handler - prefer HBCI 3.0 for KUmsAllCamt
            HBCIHandler handler = null;
            try {
                System.out.println("[INIT] Trying HBCI 3.0...");
                handler = new HBCIHandler("300", passport);
                System.out.println("[INIT] ✓ HBCI 3.0 initialized");
            } catch (Exception e) {
                System.out.println("[INIT] HBCI 3.0 failed, trying 2.2: " + e.getMessage());
                handler = new HBCIHandler("220", passport);
                System.out.println("[INIT] ✓ HBCI 2.2 initialized");
            }

            System.out.println();
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println("  Multi-Job Dialog: Building " + maxChunks + " KUmsAllCamt jobs");
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println();

            // Build Konto object
            Konto k = new Konto();
            k.iban = iban;
            k.blz = blz;
            if (!bic.isEmpty()) {
                k.bic = bic;
            } else {
                System.out.println("WARNING: BIC not set - may cause issues with KUmsAllCamt");
            }
            k.curr = "EUR";
            k.number = accountNumberFromIban(iban);

            // Build date windows (each chunk = yearsPerChunk backwards)
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.YEAR, -1); // Start 1 year ago for historical sync
            
            List<HBCIJob> jobs = new ArrayList<>();
            
            for (int i = 0; i < maxChunks; i++) {
                Date chunkStart = calendar.getTime();
                calendar.add(Calendar.YEAR, -yearsPerChunk);
                Date chunkEnd = calendar.getTime();
                
                System.out.println("Job " + (i + 1) + "/" + maxChunks + ":");
                System.out.println("  Job Type: KUmsAllCamt");
                System.out.println("  startdate: " + sdf.format(chunkStart));
                if (useEnddate) {
                    System.out.println("  enddate: " + sdf.format(chunkEnd));
                } else {
                    System.out.println("  enddate: (not set - let bank decide)");
                }
                
                try {
                    HBCIJob job = handler.newJob("KUmsAllCamt");
                    job.setParam("my", k);
                    job.setParam("startdate", sdf.format(chunkStart));
                    if (useEnddate) {
                        job.setParam("enddate", sdf.format(chunkEnd));
                    }
                    handler.addJob(job);
                    jobs.add(job);
                    System.out.println("  ✓ Job added to dialog");
                } catch (Exception e) {
                    System.err.println("  ✗ Job failed: " + e.getMessage());
                    if (i == 0) {
                        throw e; // First job must succeed
                    }
                }
                System.out.println();
            }

            if (jobs.isEmpty()) {
                System.err.println("ERROR: No jobs could be created");
                System.exit(1);
            }

            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println("  Executing Multi-Job Dialog (ONE TAN!)");
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println();
            System.out.println("Jobs in dialog: " + jobs.size());
            System.out.println("Expected result: ONE TAN request for all jobs");
            System.out.println();

            // SINGLE EXECUTE for all jobs → ONE TAN!
            HBCIExecStatus status = handler.execute();
            
            if (!status.isOK()) {
                System.err.println("ERROR: Multi-Job Dialog failed: " + status);
                System.exit(1);
            }
            System.out.println("✓ Multi-Job Dialog executed successfully");
            System.out.println();

            // Parse results from ALL jobs
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println("  Parsing Job Results");
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println();

            int totalTransactions = 0;
            for (int i = 0; i < jobs.size(); i++) {
                HBCIJob job = jobs.get(i);
                System.out.println("─── Job " + (i + 1) + "/" + jobs.size() + " ───");
                
                GVRKUms result = (GVRKUms) job.getJobResult();
                if (result == null) {
                    System.out.println("  ✗ No GVRKUms result");
                    continue;
                }

                // Try to get flatData
                List<Object> entriesList = null;
                try {
                    java.lang.reflect.Method getFlatData = result.getClass().getMethod("getFlatData");
                    Object flatDataResult = getFlatData.invoke(result);
                    if (flatDataResult instanceof List) {
                        entriesList = (List<Object>) flatDataResult;
                    } else if (flatDataResult instanceof Object[]) {
                        entriesList = Arrays.asList((Object[]) flatDataResult);
                    } else {
                        System.err.println("  ✗ Unexpected flatData type: " + (flatDataResult != null ? flatDataResult.getClass().getName() : "null"));
                        continue;
                    }
                } catch (Exception e) {
                    System.err.println("  ✗ Error getting flatData: " + e.getMessage());
                    continue;
                }

                if (entriesList == null || entriesList.isEmpty()) {
                    System.out.println("  ⚠ No transactions (empty result)");
                    continue;
                }

                System.out.println("  ✓ Extracted " + entriesList.size() + " transactions");
                totalTransactions += entriesList.size();

                // Print first 3 transactions as sample
                int sampleCount = Math.min(3, entriesList.size());
                for (int j = 0; j < sampleCount; j++) {
                    Object entry = entriesList.get(j);
                    try {
                        java.lang.reflect.Method getValue = entry.getClass().getMethod("getValue", String.class);
                        Value amount = (Value) getValue.invoke(entry, "value");
                        Date valuta = (Date) getValue.invoke(entry, "valuta");
                        Object usageObj = getValue.invoke(entry, "usage");
                        String usage = usageObj != null ? usageObj.toString() : "";
                        
                        System.out.println("    TX " + (j + 1) + ": " + 
                            (valuta != null ? sdf.format(valuta) : "?") + " | " + 
                            (amount != null ? amount.toString() : "?") + " | " +
                            (usage.length() > 50 ? usage.substring(0, 50) + "..." : usage));
                    } catch (Exception e) {
                        System.out.println("    TX " + (j + 1) + ": Parse error");
                    }
                }
                if (entriesList.size() > sampleCount) {
                    System.out.println("    ... and " + (entriesList.size() - sampleCount) + " more");
                }
                System.out.println();
            }

            // Close
            handler.close();

            System.out.println("╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║                      RESULT SUMMARY                            ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("Jobs executed: " + jobs.size() + " (in ONE dialog)");
            System.out.println("Total transactions: " + totalTransactions);
            System.out.println("TAN requests: 1 (expected)");
            System.out.println();
            
            if (totalTransactions > 150) {
                System.out.println("✓✓✓ SUCCESS: Multi-Job Strategy works! Got more than 150 TX!");
            } else if (totalTransactions == 0) {
                System.out.println("✗✗✗ FAILED: No transactions at all (BBBank limitation?)");
            } else if (totalTransactions <= 150 && jobs.size() > 1) {
                System.out.println("⚠⚠⚠ PARTIAL: Only first job returned data (same issue as before)");
            } else {
                System.out.println("✓ OK: Got " + totalTransactions + " transactions");
            }
            System.out.println();

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // === Helper methods (copied from BbbankSync.java) ===

    private static Properties loadConfig() throws Exception {
        Properties props = new Properties();
        File configFile = new File("config.properties");
        if (!configFile.exists()) {
            configFile = new File("scripts/java-sync/config.properties");
        }
        if (configFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                props.load(reader);
            }
        }
        return props;
    }

    private static String blzFromIban(String iban) {
        String normalized = iban.replace(" ", "").toUpperCase();
        if (normalized.length() == 22 && normalized.startsWith("DE")) {
            return normalized.substring(4, 12);
        }
        throw new IllegalArgumentException("Ungültige deutsche IBAN: " + iban);
    }

    private static String accountNumberFromIban(String iban) {
        String normalized = iban.replace(" ", "").toUpperCase();
        if (normalized.length() == 22 && normalized.startsWith("DE")) {
            return normalized.substring(12, 22);
        }
        return "";
    }

    private static String maskIban(String iban) {
        String normalized = iban.replace(" ", "").toUpperCase();
        if (normalized.length() >= 4) {
            return "***" + normalized.substring(normalized.length() - 4);
        }
        return iban;
    }

    private static String maskUser(String user) {
        if (user.length() > 4) {
            return user.substring(0, 2) + "***" + user.substring(user.length() - 2);
        }
        return "***";
    }

    private static void initHbci(boolean debug, String userId, String pin, String blz) {
        Properties props = new Properties();
        props.setProperty("client.product.id", "MyBudgetsMultiJobTest");
        props.setProperty("client.product.version", "1.0");
        props.setProperty("log.loglevel.default", debug ? "4" : "2");
        props.setProperty("comm.standard.sktimeout", "60000");
        props.setProperty("comm.standard.sktconnect", "30000");

        HBCIUtils.init(props, new SimpleCallback(userId, pin, blz));
    }

    static class SimpleCallback extends AbstractHBCICallback {
        private String userId;
        private String pin;
        private String blz;

        public SimpleCallback(String userId, String pin, String blz) {
            this.userId = userId;
            this.pin = pin;
            this.blz = blz;
        }

        @Override
        public void callback(org.kapott.hbci.passport.HBCIPassport passport, int reason,
                           String msg, int datatype, StringBuffer retData) {
            switch (reason) {
                case NEED_COUNTRY:
                    retData.replace(0, retData.length(), "DE");
                    break;
                case NEED_BLZ:
                    retData.replace(0, retData.length(), blz);
                    break;
                case NEED_USERID:
                case NEED_CUSTOMERID:
                    retData.replace(0, retData.length(), userId);
                    break;
                case NEED_HOST:
                    retData.replace(0, retData.length(), "fints2.atruvia.de/cgi-bin/hbciservlet");
                    break;
                case NEED_PORT:
                    retData.replace(0, retData.length(), "443");
                    break;
                case NEED_FILTER:
                    retData.replace(0, retData.length(), "Base64");
                    break;
                case NEED_PT_PIN:
                    retData.replace(0, retData.length(), pin);
                    break;
                case NEED_PT_TAN:
                    System.out.println();
                    System.out.println("╔════════════════════════════════════════════════════════════════╗");
                    System.out.println("║                    TAN REQUIRED                                ║");
                    System.out.println("╚════════════════════════════════════════════════════════════════╝");
                    System.out.println();
                    System.out.println("Message: " + msg);
                    System.out.print("Enter TAN: ");
                    try {
                        String tan = new BufferedReader(new InputStreamReader(System.in)).readLine();
                        retData.replace(0, retData.length(), tan);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    System.out.println();
                    break;
                case NEED_PT_SECMECH:
                    // TAN-Method selection (reason=27)
                    // retData contains available methods as pipe-delimited list: "code:name|code:name|..."
                    System.out.println();
                    System.out.println("[TAN-METHOD] Selection requested");
                    System.out.println("Message: " + msg);
                    
                    String availableMethods = retData != null ? retData.toString() : "";
                    System.out.println("Available methods: " + availableMethods);
                    
                    // Parse pipe-delimited list and extract first method code
                    String selectedMethod = "";
                    if (availableMethods.contains("|")) {
                        String[] methods = availableMethods.split("\\|");
                        for (String method : methods) {
                            String code = method.split(":")[0].trim();
                            String name = method.contains(":") ? method.split(":")[1].trim() : "";
                            System.out.println("  " + code + ": " + name);
                            if (selectedMethod.isEmpty()) {
                                selectedMethod = code; // Use first available
                            }
                        }
                    } else if (!availableMethods.isEmpty()) {
                        // Single method without pipe
                        selectedMethod = availableMethods.split(":")[0].trim();
                        System.out.println("  Single method: " + selectedMethod);
                    }
                    
                    if (!selectedMethod.isEmpty()) {
                        retData.replace(0, retData.length(), selectedMethod);
                        System.out.println("[TAN-METHOD] Selected: " + selectedMethod);
                    } else {
                        System.out.println("[TAN-METHOD] WARNING: No methods found in retData!");
                    }
                    break;
                case NEED_PASSPHRASE_LOAD:
                case NEED_PASSPHRASE_SAVE:
                    retData.replace(0, retData.length(), "default-passphrase");
                    break;
                default:
                    System.out.println("[CALLBACK] reason=" + reason + " msg=" + msg);
                    if (retData != null && retData.length() > 0) {
                        retData.replace(0, retData.length(), "");
                    }
            }
        }

        @Override
        public void log(String msg, int level, Date date, java.lang.StackTraceElement trace) {
            if (level <= 2) {
                System.out.println("[HBCI] " + msg);
            }
        }

        @Override
        public void status(org.kapott.hbci.passport.HBCIPassport passport, int statusTag, Object[] obj) {
            // Optional status callback
        }
    }
}
