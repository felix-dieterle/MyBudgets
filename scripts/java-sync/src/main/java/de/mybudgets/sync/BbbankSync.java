package de.mybudgets.sync;

import org.kapott.hbci.GV.HBCIJob;
import org.kapott.hbci.GV_Result.GVRKUms;
import org.kapott.hbci.callback.AbstractHBCICallback;
import org.kapott.hbci.exceptions.HBCI_Exception;
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
import java.util.Date;
import java.util.Properties;

/**
 * Simple BBBank FinTS sync using hbci4java
 * Analog to FintsService.kt in the Android app
 */
public class BbbankSync {

    private static final String TAG = "BbbankSync";

    public static void main(String[] args) {
        try {
            // Load configuration from properties file
            Properties props = loadConfig();
            String iban = props.getProperty("iban");
            String userId = props.getProperty("userId");
            String pin = props.getProperty("pin");
            String blz = props.getProperty("blz");
            String tanMethod = props.getProperty("tanMethod", "");
            int daysBack = Integer.parseInt(props.getProperty("daysBack", "30"));
            boolean debug = Boolean.parseBoolean(props.getProperty("debug", "false"));

            // Check required fields
            if (iban == null || userId == null || pin == null) {
                System.err.println("Fehler: Folgende Pflichtfelder fehlen in config.properties:");
                if (iban == null) System.err.println("  - iban");
                if (userId == null) System.err.println("  - userId");
                if (pin == null) System.err.println("  - pin");
                System.err.println();
                System.err.println("Aktuelle Konfiguration:");
                System.err.println("  iban: " + (iban != null ? maskIban(iban) : "(nicht gesetzt)"));
                System.err.println("  userId: " + (userId != null ? maskUser(userId) : "(nicht gesetzt)"));
                System.err.println("  pin: " + (pin != null ? "***" : "(nicht gesetzt)"));
                System.err.println("  blz: " + (blz != null && !blz.isEmpty() ? blz : "(nicht gesetzt)"));
                System.err.println("  tanMethod: " + (tanMethod != null && !tanMethod.isEmpty() ? tanMethod : "(nicht gesetzt)"));
                System.err.println("  daysBack: " + daysBack);
                System.err.println("  debug: " + debug);
                System.exit(2);
            }

            // Derive BLZ from IBAN if not provided
            if (blz == null || blz.isEmpty()) {
                blz = blzFromIban(iban);
            }

            System.out.println("=== BBBank FinTS Sync ===");
            System.out.println("IBAN: " + maskIban(iban));
            System.out.println("BLZ: " + blz);
            System.out.println("User: " + maskUser(userId));
            System.out.println("TAN-Methode: " + (tanMethod.isEmpty() ? "(auto)" : tanMethod));
            System.out.println("Zeitraum: letzte " + daysBack + " Tage");
            System.out.println();

            // Initialize HBCI
            initHbci(debug, userId, pin, blz);

            // Setup passport directory
            File passportDir = new File("scripts/java-sync/passports");
            passportDir.mkdirs();
            File passportFile = new File(passportDir, "passport_" + blz + ".dat");

            HBCIUtils.setParam("client.passport.PinTan.filename", passportFile.getAbsolutePath());
            HBCIUtils.setParam("client.passport.PinTan.init", "1");
            HBCIUtils.setParam("client.passport.default", "PinTan");

            // Create passport like in Android app
            AbstractHBCIPassport passport = (AbstractHBCIPassport) AbstractHBCIPassport.getInstance("PinTan");

            System.out.println("Passport class: " + passport.getClass().getName());
            System.out.println("Passport superclass: " + passport.getClass().getSuperclass().getName());

            // Set fields using setter methods (Kotlin properties become Java methods)
            try {
                java.lang.reflect.Method setCountry = passport.getClass().getMethod("setCountry", String.class);
                setCountry.invoke(passport, "DE");
                System.out.println("Set country to DE");
            } catch (Exception e) {
                System.err.println("Could not set country: " + e.getMessage());
            }

            // BLZ will be set via callback instead

            try {
                java.lang.reflect.Method setUserId = passport.getClass().getMethod("setUserId", String.class);
                setUserId.invoke(passport, userId);
                System.out.println("Set userId to " + maskUser(userId));
            } catch (Exception e) {
                System.err.println("Could not set userId: " + e.getMessage());
            }

            try {
                java.lang.reflect.Method setCustomerId = passport.getClass().getMethod("setCustomerId", String.class);
                setCustomerId.invoke(passport, userId);
                System.out.println("Set customerId to " + maskUser(userId));
            } catch (Exception e) {
                System.err.println("Could not set customerId: " + e.getMessage());
            }

            if (!tanMethod.isEmpty()) {
                HBCIUtils.setParam("client.passport.PinTan.tanmethod", tanMethod);
            }

            // Create handler
            // Try HBCI 2.2 first (no system ID sync needed), fallback to 3.0
            HBCIHandler handler = null;
            try {
                handler = new HBCIHandler("220", passport);
            } catch (Exception e) {
                System.out.println("HBCI 2.2 fehlgeschlagen, versuche 3.0: " + e.getMessage());
                handler = new HBCIHandler("300", passport);
            }

            // Fetch account statement
            System.out.println("[1/3] Kontoauszug abrufen...");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date startDate = new Date(System.currentTimeMillis() - (daysBack * 24L * 60 * 60 * 1000));
            Date endDate = new Date();

            // Try different job types (fallback strategy)
            HBCIJob job = null;
            String[] jobTypes = {"KUmsAllCamt", "KUmsZeitSEPA", "KUmsAll", "KUmsNew"};
            Exception lastError = null;

            for (String jobType : jobTypes) {
                try {
                    System.out.println("  Versuche Job-Typ: " + jobType);
                    job = handler.newJob(jobType);

                    // Set account parameters
                    Konto k = new Konto();
                    k.iban = iban;
                    k.blz = blz;
                    k.curr = "EUR";
                    k.number = accountNumberFromIban(iban);

                    job.setParam("my", k);

                    if (jobType.equals("KUmsAllCamt") || jobType.equals("KUmsZeitSEPA")) {
                        job.setParam("startdate", sdf.format(startDate));
                        job.setParam("enddate", sdf.format(endDate));
                    }

                    handler.addJob(job);
                    System.out.println("  Job " + jobType + " erfolgreich hinzugefügt");
                    break;
                } catch (Exception e) {
                    System.out.println("  Job " + jobType + " fehlgeschlagen: " + e.getMessage());
                    lastError = e;
                }
            }

            if (job == null) {
                System.err.println("Fehler: Kein Job-Typ konnte initialisiert werden");
                if (lastError != null) {
                    lastError.printStackTrace();
                }
                System.exit(1);
            }

            // Execute
            System.out.println("[2/3] Sende Anfrage an Bank...");
            HBCIExecStatus status = handler.execute();
            if (!status.isOK()) {
                System.err.println("Fehler: Bank-Antwort nicht OK: " + status);
                System.exit(1);
            }
            System.out.println("  Bank-Antwort OK");

            // Parse results
            System.out.println("[3/3] Parse Ergebnisse...");
            GVRKUms result = (GVRKUms) job.getJobResult();
            if (result == null) {
                System.err.println("Fehler: Kein Ergebnis vom Job");
                System.exit(1);
            }

            // Try to get flatData using reflection
            Object[] entries = null;
            try {
                java.lang.reflect.Method getFlatData = result.getClass().getMethod("getFlatData");
                entries = (Object[]) getFlatData.invoke(result);
            } catch (Exception e) {
                System.err.println("Fehler beim Abrufen der Buchungen: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }

            if (entries == null) {
                System.err.println("Fehler: Keine Buchungen im Ergebnis");
                System.exit(1);
            }

            System.out.println("  " + entries.length + " Buchung(en) empfangen");
            System.out.println();

            // Print transactions
            for (int i = 0; i < entries.length && i < 10; i++) {
                Object entry = entries[i];
                try {
                    // Try to get values using reflection
                    java.lang.reflect.Method getValue = entry.getClass().getMethod("getValue", String.class);
                    Value amount = (Value) getValue.invoke(entry, "value");
                    Date valuta = (Date) getValue.invoke(entry, "valuta");
                    Object usageObj = getValue.invoke(entry, "usage");
                    String usage = usageObj != null ? usageObj.toString() : "";
                    Object otherNameObj = getValue.invoke(entry, "other.name");
                    String otherName = otherNameObj != null ? otherNameObj.toString() : "";

                    System.out.println("Buchung " + (i + 1) + ":");
                    System.out.println("  Datum: " + (valuta != null ? sdf.format(valuta) : "?"));
                    System.out.println("  Betrag: " + (amount != null ? amount.toString() : "?"));
                    if (!usage.isEmpty()) {
                        System.out.println("  Verwendung: " + usage.substring(0, Math.min(100, usage.length())));
                    }
                    if (!otherName.isEmpty()) {
                        System.out.println("  Name: " + otherName);
                    }
                    System.out.println();
                } catch (Exception e) {
                    System.out.println("Buchung " + (i + 1) + ": Fehler beim Parsen: " + e.getMessage());
                }
            }

            if (entries.length > 10) {
                System.out.println("... und " + (entries.length - 10) + " weitere Buchungen");
            }

            // Close
            handler.close();
            System.out.println("=== SYNC ERFOLGREICH ===");

        } catch (Exception e) {
            System.err.println("Fehler: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static Properties loadConfig() throws Exception {
        Properties props = new Properties();
        // Try config.properties in current directory first
        File configFile = new File("config.properties");
        if (!configFile.exists()) {
            // Fallback to scripts/java-sync/config.properties
            configFile = new File("scripts/java-sync/config.properties");
        }
        if (configFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                props.load(reader);
            }
        } else {
            // Try environment variables
            String iban = System.getenv("MYB_IBAN");
            if (iban != null) props.setProperty("iban", iban);
            String userId = System.getenv("MYB_USER");
            if (userId != null) props.setProperty("userId", userId);
            String pin = System.getenv("MYB_PIN");
            if (pin != null) props.setProperty("pin", pin);
            String blz = System.getenv("MYB_BLZ");
            if (blz != null) props.setProperty("blz", blz);
            String tanMethod = System.getenv("MYB_TAN_METHOD");
            if (tanMethod != null) props.setProperty("tanMethod", tanMethod);
            String daysBack = System.getenv("MYB_DAYS_BACK");
            if (daysBack != null) props.setProperty("daysBack", daysBack);
            String debug = System.getenv("MYB_DEBUG");
            if (debug != null) props.setProperty("debug", debug);
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
        props.setProperty("client.product.id", "MyBudgets");
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
                    retData.replace(0, retData.length(), "fints2.atruvia.de");
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
                    System.out.println("TAN erforderlich: " + msg);
                    System.out.print("TAN eingeben: ");
                    try {
                        String tan = new BufferedReader(new InputStreamReader(System.in)).readLine();
                        retData.replace(0, retData.length(), tan);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case NEED_PASSPHRASE_LOAD:
                case NEED_PASSPHRASE_SAVE:
                    retData.replace(0, retData.length(), "default-passphrase");
                    break;
                default:
                    System.out.println("Callback reason=" + reason + " msg=" + msg + " datatype=" + datatype);
                    // Try to return empty string for unknown reasons
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
            // Optional: Implement status callback if needed
        }
    }
}
