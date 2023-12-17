package scheduler;

import scheduler.db.ConnectionManager;
import scheduler.model.Caregiver;
import scheduler.model.Patient;
import scheduler.model.Vaccine;
import scheduler.util.Util;

import javax.xml.transform.Result;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

public class Scheduler {

    // objects to keep track of the currently logged-in user
    // Note: it is always true that at most one of currentCaregiver and currentPatient is not null
    //       since only one user can be logged-in at a time
    private static Caregiver currentCaregiver = null;
    private static Patient currentPatient = null;

    public static void main(String[] args) {
        greetings();
        // read input from user
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("> ");
            String response = "";
            try {
                response = r.readLine();
            } catch (IOException e) {
                System.out.println("Please try again!");
            }
            // split the user input by spaces
            String[] tokens = response.split(" ");
            // check if input exists
            if (tokens.length == 0) {
                System.out.println("Please try again!");
                continue;
            }
            // determine which operation to perform
            String operation = tokens[0];
            if (operation.equals("create_patient")) {
                createPatient(tokens);
                greetings();
            } else if (operation.equals("create_caregiver")) {
                createCaregiver(tokens);
                greetings();
            } else if (operation.equals("login_patient")) {
                loginPatient(tokens);
                greetings();
            } else if (operation.equals("login_caregiver")) {
                loginCaregiver(tokens);
                greetings();
            } else if (operation.equals("search_caregiver_schedule")) {
                searchCaregiverSchedule(tokens);
                greetings();
            } else if (operation.equals("reserve")) {
                reserve(tokens);
                greetings();
            } else if (operation.equals("upload_availability")) {
                uploadAvailability(tokens);
                greetings();
            } else if (operation.equals("cancel")) {
                cancel(tokens);
                greetings();
            } else if (operation.equals("add_doses")) {
                addDoses(tokens);
                greetings();
            } else if (operation.equals("show_appointments")) {
                showAppointments(tokens);
                greetings();
            } else if (operation.equals("logout")) {
                logout(tokens);
                greetings();
            } else if (operation.equals("quit")) {
                System.out.println("Bye!");
                return;
            } else {
                System.out.println("Invalid operation name! Please check your spelling!");
                greetings();
            }
        }
    }

    private static void greetings() {
        System.out.println();
        System.out.println("Welcome to the COVID-19 Vaccine Reservation Scheduling Application!");
        System.out.println("*** Please enter one of the following commands ***");
        System.out.println("> create_patient <username> <password>");
        System.out.println("> create_caregiver <username> <password>");
        System.out.println("> login_patient <username> <password>");
        System.out.println("> login_caregiver <username> <password>");
        System.out.println("> search_caregiver_schedule <date>");
        System.out.println("> reserve <date> <vaccine>");
        System.out.println("> upload_availability <date>");
        System.out.println("> cancel <appointment_id>");
        System.out.println("> add_doses <vaccine> <number>");
        System.out.println("> show_appointments");
        System.out.println("> logout");
        System.out.println("> quit");
        System.out.println();
    }

    private static void createPatient(String[] tokens) {
        if (tokens.length != 3) {
            System.out.println("Failed to create user. To create a patient account, please only type" +
                               " \"create_patient <username> <password>\"!");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        if (usernameExistsPatient(username)) {
            System.out.println("Username taken, try again!");
            return;
        }
        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        try {
            Patient patient = new Patient.PatientBuilder(username, salt, hash).build();
            patient.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Failed to create user.");
            e.printStackTrace();
        }
    }
    private static boolean usernameExistsPatient(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Patients WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void createCaregiver(String[] tokens) {
        // create_caregiver <username> <password>
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Failed to create user. To create a caregiver account, please only type \"create_caregiver" +
                               " <username> <password>\"");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        // check 2: check if the username has been taken already
        if (usernameExistsCaregiver(username)) {
            System.out.println("Username taken, try again!");
            return;
        }
        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        // create the caregiver
        try {
            Caregiver caregiver = new Caregiver.CaregiverBuilder(username, salt, hash).build(); 
            // save to caregiver information to our database
            caregiver.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Failed to create user.");
            e.printStackTrace();
        }
    }

    private static boolean usernameExistsCaregiver(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Caregivers WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void loginPatient(String[] tokens) {
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in.");
            return;
        }
        if (tokens.length != 3) {
            System.out.println("Login failed. To login as a patient, please only type \"login_patient <username>" +
                               " <password>\"");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        Patient patient = null;
        try {
            patient = new Patient.PatientGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }
        // check if the login was successful
        if (patient == null) {
            System.out.println("Login failed.");
        } else {
            System.out.println("Logged in as: " + username);
            currentPatient = patient;
        }
    }

    private static void loginCaregiver(String[] tokens) {
        // login_caregiver <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in.");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Login failed. To login as a caregiver, please only type \"login_caregiver <username>" +
                               " <password>\"");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Caregiver caregiver = null;
        try {
            caregiver = new Caregiver.CaregiverGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }
        // check if the login was successful
        if (caregiver == null) {
            System.out.println("Login failed.");
        } else {
            System.out.println("Logged in as: " + username);
            currentCaregiver = caregiver;
        }
    }

    // done?
    // needs time
    private static void searchCaregiverSchedule(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }
        if (tokens.length != 2) {
            System.out.println("Please try again! Please only type \"search_caregiver_schedule " +
                               "YYYY-MM-DD\"");
            return;
        }
        String date = tokens[1];
        String userAvailable = "SELECT Username FROM Availabilities WHERE Time = ? " +
                               "ORDER BY Username asc";
        String vaccineSelected = "SELECT * FROM Vaccines";
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        try {
            PreparedStatement users = con.prepareStatement(userAvailable);
            PreparedStatement vaccines = con.prepareStatement(vaccineSelected);
            Date d = Date.valueOf(date);
            users.setDate(1, d);
            ResultSet userSet = users.executeQuery();
            ResultSet vacSet = vaccines.executeQuery();
            if (!userSet.next()) {
                System.out.println("No caregivers available for this date!");
            } else {
                do {
                    System.out.println("Caregiver: " + userSet.getString(1) + " ");
                } while (userSet.next());
            }
            while (vacSet.next()) {
                System.out.print(vacSet.getString(1) + " ");
                System.out.println(vacSet.getString(2) + " doses left");
            }
        } catch (IllegalArgumentException e1) {
            System.out.println("Please enter a valid date in format YYYY-MM-DD");
        } catch (SQLException e2) {
            System.out.println("Error occurred while searching schedule");
            e2.printStackTrace();
        } finally {
            cm.closeConnection();
        }
    }

    // date + vaccine
    private static void reserve(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }
        if (currentCaregiver != null) {
            System.out.println("Please login as a patient!");
            return;
        }
        if (tokens.length != 3) {
            System.out.println("Please try again! To reserve an appointment, please only type \"reserve YYYY-MM-DD" +
                               " <vaccine>\"!");
            return;
        }
        String date = tokens[1];
        String vaccineValid = tokens[2];
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        try {
            // check if there are vaccines
            Vaccine vaccine = new Vaccine.VaccineGetter(vaccineValid).get();
            if (vaccine == null) {
                System.out.println("Please check your spelling, and enter a valid vaccine!");
                return;
            }
            int doses = vaccine.getAvailableDoses();
            if (doses < 1) {
                System.out.println("Not enough available doses!");
                return;
            }
            String availableCaregivers = "SELECT Username FROM Availabilities " +
                                         "WHERE Time = ? ORDER BY Username";
            PreparedStatement statement1 = con.prepareStatement(availableCaregivers);
            Date d = Date.valueOf(date);
            statement1.setDate(1, d);
            ResultSet availCare = statement1.executeQuery();
            List<String> usernames = new ArrayList<>();
            while (availCare.next()) {
                usernames.add(availCare.getString(1));
            }
            if (usernames.size() == 0) {
                System.out.println("No caregiver is available!");
                return;
            }
            vaccine.decreaseAvailableDoses(1);
            String makeAppointment = "INSERT INTO Appointments (date, p_user, c_user, v_name) " +
                                     "VALUES (?, ?, ?, ?)";
            PreparedStatement statement2 = con.prepareStatement(makeAppointment);
            statement2.setDate(1, d);
            statement2.setString(2, currentPatient.getUsername());
            statement2.setString(3, usernames.get(0));
            statement2.setString(4, vaccineValid);
            statement2.executeUpdate();
            String dropAvailability = "DELETE FROM Availabilities " +
                                      "WHERE Username = ? AND Time = ?";
            PreparedStatement statement3 = con.prepareStatement(dropAvailability);
            statement3.setString(1, usernames.get(0));
            statement3.setDate(2, d);
            statement3.execute();
            String findAptID = "SELECT a_id FROM Appointments " +
                               "WHERE date = ? AND c_user = ?";
            PreparedStatement statement4 = con.prepareStatement(findAptID);
            statement4.setDate(1, d);
            statement4.setString(2, usernames.get(0));
            ResultSet resultSet = statement4.executeQuery();
            while(resultSet.next()) {
                System.out.println("Appointment ID: " + resultSet.getString(1) + ", " +
                        "Caregiver username: " + usernames.get(0));
            }
        } catch (IllegalArgumentException e1) {
            System.out.println("Please enter a valid date in format YYYY-MM-DD");
        } catch (SQLException e2) {
            System.out.println("Error occurred while reserving appointment");
            e2.printStackTrace();
        } finally {
            cm.closeConnection();
        }
    }

    private static void uploadAvailability(String[] tokens) {
        // upload_availability <date>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Please try again! To upload availability, " +
                               "please only type \"upload_availability YYYY-MM-DD\"");
            return;
        }
        String date = tokens[1];
        try {
            Date d = Date.valueOf(date);
            currentCaregiver.uploadAvailability(d);
            System.out.println("Availability uploaded!");
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date in format YYYY-MM-DD");
        } catch (SQLException e) {
            System.out.println("Error occurred when uploading availability");
            e.printStackTrace();
        }
    }

    private static void cancel(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }
        if (tokens.length != 2) {
            System.out.println("Please try again! To cancel an appointment, " +
                               "please only type \"cancel <appointment_id>\"");
            return;
        }
        int id = Integer.parseInt(tokens[1]);
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        try {
            String findDate;
            if (currentCaregiver != null) {
                findDate = "SELECT date, c_user, v_name FROM Appointments " +
                           "WHERE a_id = ? AND c_user = ?";
            } else {
                findDate = "SELECT date, c_user, v_name FROM Appointments " +
                           "WHERE a_id = ? AND p_user = ?";
            }
            PreparedStatement statement1 = con.prepareStatement(findDate);
            statement1.setInt(1, id);
            if (currentCaregiver != null) {
                statement1.setString(2, currentCaregiver.getUsername());
            } else {
                statement1.setString(2, currentPatient.getUsername());
            }
            ResultSet resultSet = statement1.executeQuery();
            if(resultSet.next()) {
                String dropAppt = "DELETE FROM Appointments " +
                                  "WHERE a_id = ?";
                PreparedStatement statement2 = con.prepareStatement(dropAppt);
                statement2.setInt(1, id);
                statement2.executeUpdate();
                String updateAvailability = "INSERT INTO Availabilities(Time, Username) VALUES (?, ?)";
                Vaccine vaccine = null;
                vaccine = new Vaccine.VaccineGetter(resultSet.getString(3)).get();
                vaccine.increaseAvailableDoses(1);
                PreparedStatement statement3 = con.prepareStatement(updateAvailability);
                statement3.setDate(1, resultSet.getDate(1));
                statement3.setString(2, resultSet.getString(2));
                statement3.executeUpdate();
                System.out.println("Appointment successfully cancelled!");
            } else {
                System.out.println("Unable to cancel appointment! Please make sure you're logged into the right" +
                                   " account and double check the appointment ID.");
            }
        } catch (SQLException e) {
            System.out.println("Error occurred while cancelling appointment");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
    }

    private static void addDoses(String[] tokens) {
        // add_doses <vaccine> <number>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again! To add doses, please only type \"add_doses <vaccine> <number\"");
            return;
        }
        String vaccineName = tokens[1];
        int doses = Integer.parseInt(tokens[2]);
        Vaccine vaccine = null;
        try {
            vaccine = new Vaccine.VaccineGetter(vaccineName).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when adding doses");
            e.printStackTrace();
        }
        // check 3: if getter returns null, it means that we need to create the vaccine and insert it into the Vaccines
        //          table
        if (vaccine == null) {
            try {
                vaccine = new Vaccine.VaccineBuilder(vaccineName, doses).build();
                vaccine.saveToDB();
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        } else {
            // if the vaccine is not null, meaning that the vaccine already exists in our table
            try {
                vaccine.increaseAvailableDoses(doses);
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        }
        System.out.println("Doses updated!");
    }

    private static void showAppointments(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }
        if (tokens.length != 1) {
            System.out.println("Please try again! To view appointments, please only type \"show_appointments\"!");
            return;
        }
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();
        if (currentCaregiver != null) {
            String findAppointments = "SELECT a_id, v_name, date, p_user FROM Appointments " +
                                      "WHERE c_user = ? ORDER BY a_id";
            try {
                PreparedStatement statement = con.prepareStatement(findAppointments);
                statement.setString(1, currentCaregiver.getUsername());
                ResultSet resultSet = statement.executeQuery();
                if(!resultSet.next()) {
                    System.out.println("You do not have any appointments scheduled!");
                } else {
                    System.out.println("ApptID  Vaccine  Date  Patient");
                    do {
                        System.out.println(resultSet.getInt(1) + " " + resultSet.getString(2) + " "
                                + resultSet.getString(3) + " " + resultSet.getString(4));
                    } while (resultSet.next());
                }
            } catch (SQLException e) {
                System.out.println("Error occurred while showing appointments");
                e.printStackTrace();
            } finally {
                cm.closeConnection();
            }
        } else {
            String findAppointments = "SELECT a_id, v_name, date, c_user FROM Appointments " +
                                      "WHERE p_user = ?";
            try {
                PreparedStatement statement = con.prepareStatement(findAppointments);
                statement.setString(1, currentPatient.getUsername());
                ResultSet resultSet = statement.executeQuery();
                if (!resultSet.next()) {
                    System.out.println("You do not have any appointments scheduled!");
                } else {
                    System.out.println("ApptId  Vaccine  Date  Caregiver");
                    do {
                        System.out.println(resultSet.getInt(1) + " " + resultSet.getString(2) + " "
                                + resultSet.getString(3) + " " + resultSet.getString(4));
                    } while (resultSet.next());
                }
            } catch (SQLException e) {
                System.out.println("Error occurred while showing appointments");
                e.printStackTrace();
            } finally {
                cm.closeConnection();
            }
        }
    }

    private static void logout(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        } else if (tokens.length != 1) {
            System.out.println("Please try again! To log out, please only type \"logout\"!");
            return;
        }
        currentCaregiver = null;
        currentPatient = null;
        System.out.println("Successfully logged out!");
    }
}
