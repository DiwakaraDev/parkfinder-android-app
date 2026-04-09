package com.javainstitute.parkfinder;

import android.content.Context;

import java.util.Arrays;
import java.util.List;

public class SriLankaLocationUtil {
    /**
     * Returns a static list of Sri Lankan towns and cities.
     */
    public static List<String> getAllSriLankaLocations(Context context) {
        return Arrays.asList(
                "Ampara", "Anuradhapura", "Badulla", "Batticaloa", "Colombo", "Galle", "Gampaha", "Hambantota",
                "Jaffna", "Kalutara", "Kandy", "Kegalle", "Kilinochchi", "Kurunegala", "Mannar", "Matale",
                "Matara", "Moneragala", "Mullaitivu", "Nuwara Eliya", "Polonnaruwa", "Puttalam", "Ratnapura",
                "Trincomalee", "Vavuniya", "Negombo", "Dehiwala-Mount Lavinia", "Moratuwa", "Sri Jayawardenepura Kotte",
                "Beruwala", "Chilaw", "Gampola", "Hatton", "Kaduwela", "Kandana", "Kattankudy", "Kelaniya",
                "Maharagama", "Nawalapitiya", "Panadura", "Point Pedro", "Valvettithurai", "Wattala",
                "Akkaraipattu", "Aluthgama", "Ambalangoda", "Ambalantota", "Athurugiriya", "Avissawella", "Bandarawela",
                "Bentota", "Boralesgamuwa", "Chavakachcheri", "Dambulla", "Dankotuwa", "Deniyaya", "Divulapitiya",
                "Diyatalawa", "Elpitiya", "Embilipitiya", "Eravur", "Galgamuwa", "Galenbindunuwewa", "Ginigathena",
                "Haputale", "Horana", "Inuvil", "Ja-Ela", "Kalawana", "Kamburupitiya", "Kesbewa", "Kiribathgoda",
                "Mannar", "Marawila", "Monaragala", "Mount Lavinia", "Mullaitivu", "Nittambuwa", "Padukka",
                "Peliyagoda", "Peradeniya", "Seeduwa", "Tangalle", "Thalawakele"
                // Add more cities/towns if desired
        );
    }
}