package com.app.datadistribution.service.impl;

import com.app.datadistribution.service.interfaces.ILocationService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class LocationServiceImpl implements ILocationService {

    private static final Map<String, List<String>> STATE_CITY_MAP = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    static {
        STATE_CITY_MAP.put("Andhra Pradesh", List.of(
            "Visakhapatnam", "Vijayawada", "Guntur", "Nellore", "Kurnool", "Rajahmundry", "Tirupati", "Kadapa", "Anantapur", "Vizianagaram", "Eluru", "Ongole", "Nandyal", "Machilipatnam", "Tenali", "Chittoor", "Hindupur", "Proddatur", "Bhimavaram", "Madanapalle"
        ));
        STATE_CITY_MAP.put("Arunachal Pradesh", List.of(
            "Itanagar", "Naharlagun", "Pasighat", "Namsai", "Tawang", "Ziro", "Roing", "Bomdila", "Tezu", "Aalo"
        ));
        STATE_CITY_MAP.put("Assam", List.of(
            "Guwahati", "Silchar", "Dibrugarh", "Jorhat", "Nagaon", "Tinsukia", "Tezpur", "Bongaigaon", "Karimganj", "Sivasagar", "Goalpara", "Barpeta", "Dhubri", "North Lakhimpur", "Diphu"
        ));
        STATE_CITY_MAP.put("Bihar", List.of(
            "Patna", "Gaya", "Bhagalpur", "Muzaffarpur", "Purnia", "Darbhanga", "Bihar Sharif", "Arrah", "Begusarai", "Katihar", "Munger", "Chhapra", "Danapur", "Saharsa", "Sasaram", "Hajipur", "Dehri", "Siwan", "Motihari", "Nawada", "Bettiah"
        ));
        STATE_CITY_MAP.put("Chhattisgarh", List.of(
            "Raipur", "Bhilai", "Bilaspur", "Korba", "Rajnandgaon", "Raigarh", "Jagdalpur", "Ambikapur", "Dhamtari", "Mahasamund", "Durg", "Kanker"
        ));
        STATE_CITY_MAP.put("Goa", List.of(
            "Panaji", "Margao", "Vasco da Gama", "Mapusa", "Ponda", "Bicholim", "Curchorem", "Cuncolim"
        ));
        STATE_CITY_MAP.put("Gujarat", List.of(
            "Ahmedabad", "Surat", "Vadodara", "Rajkot", "Bhavnagar", "Jamnagar", "Junagadh", "Gandhinagar", "Anand", "Navsari", "Morbi", "Nadiad", "Surendranagar", "Bharuch", "Mehsana", "Bhuj", "Porbandar", "Vapi", "Godhra", "Valsad", "Palanpur"
        ));
        STATE_CITY_MAP.put("Haryana", List.of(
            "Gurugram", "Faridabad", "Panipat", "Ambala", "Yamunanagar", "Rohtak", "Hisar", "Karnal", "Sonipat", "Panchkula", "Bhiwani", "Sirsa", "Bahadurgarh", "Jind", "Thanesar", "Kaithal", "Rewari", "Palwal"
        ));
        STATE_CITY_MAP.put("Himachal Pradesh", List.of(
            "Shimla", "Dharamshala", "Mandi", "Solan", "Baddi", "Nahan", "Kullu", "Manali", "Bilaspur", "Hamirpur", "Una", "Chamba"
        ));
        STATE_CITY_MAP.put("Jharkhand", List.of(
            "Ranchi", "Jamshedpur", "Dhanbad", "Bokaro Steel City", "Deoghar", "Phusro", "Hazaribagh", "Giridih", "Ramgarh", "Medininagar", "Chirkunda", "Chaibasa"
        ));
        STATE_CITY_MAP.put("Karnataka", List.of(
            "Bengaluru", "Mysuru", "Hubballi-Dharwad", "Mangaluru", "Belagavi", "Kalaburagi", "Davanagere", "Ballari", "Vijayapura", "Shivamogga", "Tumakuru", "Raichur", "Bidar", "Hosapete", "Gadag", "Hassan", "Udupi", "Robertsonpet", "Bhadravati", "Chitradurga", "Kolar"
        ));
        STATE_CITY_MAP.put("Kerala", List.of(
            "Thiruvananthapuram", "Kochi", "Kozhikode", "Kollam", "Thrissur", "Alappuzha", "Palakkad", "Malappuram", "Kannur", "Kottayam", "Kasaragod", "Pathanamthitta", "Idukki", "Wayanad"
        ));
        STATE_CITY_MAP.put("Madhya Pradesh", List.of(
            "Indore", "Bhopal", "Jabalpur", "Gwalior", "Ujjain", "Sagar", "Dewas", "Satna", "Ratlam", "Rewa", "Katni", "Singrauli", "Burhanpur", "Khandwa", "Bhind", "Chhindwara", "Guna", "Shivpuri", "Vidisha", "Damoh", "Mandsaur", "Khargone", "Neemuch", "Pithampur", "Hoshangabad", "Itarsi", "Sehore", "Betul", "Seoni", "Datia", "Nagda"
        ));
        STATE_CITY_MAP.put("Maharashtra", List.of(
            "Mumbai", "Pune", "Nagpur", "Thane", "Pimpri-Chinchwad", "Nashik", "Kalyan-Dombivli", "Vasai-Virar", "Aurangabad", "Navi Mumbai", "Solapur", "Mira-Bhayandar", "Bhiwandi", "Amravati", "Nanded", "Kolhapur", "Akola", "Ulhasnagar", "Sangli", "Malegaon", "Jalgaon", "Latur", "Dhule", "Ahmednagar", "Chandrapur", "Parbhani", "Ichalkaranji", "Jalna", "Ambarnath", "Bhusawal", "Panvel", "Satara", "Beed", "Yavatmal", "Gondia"
        ));
        STATE_CITY_MAP.put("Manipur", List.of(
            "Imphal", "Thoubal", "Bishnupur", "Churachandpur", "Kakching", "Ukhrul", "Senapati"
        ));
        STATE_CITY_MAP.put("Meghalaya", List.of(
            "Shillong", "Tura", "Nongpoh", "Jowai", "Baghmara", "Williamnagar", "Resubelpara"
        ));
        STATE_CITY_MAP.put("Mizoram", List.of(
            "Aizawl", "Lunglei", "Champhai", "Serchhip", "Kolasib", "Lawngtlai", "Saitual"
        ));
        STATE_CITY_MAP.put("Nagaland", List.of(
            "Kohima", "Dimapur", "Mokokchung", "Tuensang", "Wokha", "Zunheboto", "Mon"
        ));
        STATE_CITY_MAP.put("Odisha", List.of(
            "Bhubaneswar", "Cuttack", "Rourkela", "Berhampur", "Sambalpur", "Puri", "Balasore", "Bhadrak", "Baripada", "Jharsuguda", "Jeypore", "Bargarh", "Rayagada", "Bolangir"
        ));
        STATE_CITY_MAP.put("Punjab", List.of(
            "Ludhiana", "Amritsar", "Jalandhar", "Patiala", "Bathinda", "Hoshiarpur", "Mohali", "Batala", "Pathankot", "Moga", "Abohar", "Malerkotla", "Khanna", "Phagwara", "Muktsar", "Barnala", "Firozpur", "Kapurthala"
        ));
        STATE_CITY_MAP.put("Rajasthan", List.of(
            "Jaipur", "Jodhpur", "Kota", "Bikaner", "Ajmer", "Udaipur", "Bhilwara", "Alwar", "Bharatpur", "Sikar", "Pali", "Sri Ganganagar", "Kishangarh", "Baran", "Hanumangarh", "Beawar", "Dholpur", "Sawai Madhopur", "Churu", "Jhunjhunu", "Chittorgarh"
        ));
        STATE_CITY_MAP.put("Sikkim", List.of(
            "Gangtok", "Namchi", "Gyalshing", "Mangan", "Singtam", "Rangpo"
        ));
        STATE_CITY_MAP.put("Tamil Nadu", List.of(
            "Chennai", "Coimbatore", "Madurai", "Tiruchirappalli", "Salem", "Tiruppur", "Erode", "Vellore", "Thoothukudi", "Dindigul", "Thanjavur", "Ranipet", "Sivakasi", "Karur", "Udhagamandalam", "Hosur", "Nagercoil", "Kanchipuram", "Kumarakonam", "Karaikudi", "Neyveli", "Cuddalore", "Tiruvannamalai", "Pollachi", "Rajapalayam"
        ));
        STATE_CITY_MAP.put("Telangana", List.of(
            "Hyderabad", "Warangal", "Nizamabad", "Khammam", "Karimnagar", "Ramagundam", "Mahbubnagar", "Nalgonda", "Adilabad", "Suryapet", "Miryalaguda", "Siddipet", "Jagtial", "Mancherial"
        ));
        STATE_CITY_MAP.put("Tripura", List.of(
            "Agartala", "Dharmanagar", "Udaipur", "Kailashahar", "Belonia", "Khowai", "Teliamura", "Ambassa"
        ));
        STATE_CITY_MAP.put("Uttar Pradesh", List.of(
            "Lucknow", "Kanpur", "Ghaziabad", "Agra", "Meerut", "Varanasi", "Prayagraj", "Bareilly", "Aligarh", "Moradabad", "Saharanpur", "Gorakhpur", "Noida", "Firozabad", "Jhansi", "Muzaffarnagar", "Mathura", "Budaun", "Rampur", "Shahjahanpur", "Farrukhabad", "Ayodhya", "Hapur", "Etawah", "Mirzapur", "Bulandshahr", "Sambhal", "Amroha", "Hardoi", "Fatehpur", "Raebareli", "Orai", "Sitapur", "Bahraich", "Modinagar", "Unnao", "Jaunpur", "Lakhimpur", "Hathras", "Banda", "Pilibhit", "Greater Noida"
        ));
        STATE_CITY_MAP.put("Uttarakhand", List.of(
            "Dehradun", "Haridwar", "Roorkee", "Haldwani", "Rudrapur", "Kashipur", "Rishikesh", "Pithoragarh", "Ramnagar", "Manglaur", "Kotdwar", "Mussoorie", "Nainital", "Almora"
        ));
        STATE_CITY_MAP.put("West Bengal", List.of(
            "Kolkata", "Howrah", "Asansol", "Siliguri", "Durgapur", "Bardhaman", "Malda", "Baharampur", "Habra", "Kharagpur", "Shantipur", "Dankuni", "Dhulian", "Ranaghat", "Haldia", "Raiganj", "Krishnanagar", "Nabadwip", "Medinipur", "Jalpaiguri", "Balurghat", "Basirhat", "Bankura", "Chakdaha", "Darjeeling", "Alipurduar", "Purulia"
        ));
        STATE_CITY_MAP.put("Andaman and Nicobar Islands", List.of(
            "Port Blair", "Diglipur", "Mayabunder", "Rangat", "Garacharma"
        ));
        STATE_CITY_MAP.put("Chandigarh", List.of(
            "Chandigarh"
        ));
        STATE_CITY_MAP.put("Dadra and Nagar Haveli and Daman and Diu", List.of(
            "Daman", "Diu", "Silvassa"
        ));
        STATE_CITY_MAP.put("Delhi", List.of(
            "New Delhi", "North Delhi", "South Delhi", "East Delhi", "West Delhi", "Central Delhi", "North East Delhi", "North West Delhi", "South East Delhi", "South West Delhi", "Shahdara"
        ));
        STATE_CITY_MAP.put("Jammu and Kashmir", List.of(
            "Srinagar", "Jammu", "Anantnag", "Baramulla", "Kathua", "Udhampur", "Sopore", "Punch", "Rajouri", "Ganderbal", "Kulgam", "Pulwama", "Kupwara", "Budgam"
        ));
        STATE_CITY_MAP.put("Ladakh", List.of(
            "Leh", "Kargil"
        ));
        STATE_CITY_MAP.put("Lakshadweep", List.of(
            "Kavaratti", "Agatti", "Amini", "Andrott", "Minicoy"
        ));
        STATE_CITY_MAP.put("Puducherry", List.of(
            "Puducherry", "Karaikal", "Mahe", "Yanam", "Ozhukarai"
        ));
    }

    @Override
    public List<String> getStates() {
        return new ArrayList<>(STATE_CITY_MAP.keySet());
    }

    @Override
    public List<String> getCitiesByState(String state) {
        if (state == null || state.isBlank()) {
            return Collections.emptyList();
        }
        List<String> cities = STATE_CITY_MAP.get(state.trim());
        if (cities != null) {
            return new ArrayList<>(cities);
        }
        // Case-insensitive lookup fallback
        for (Map.Entry<String, List<String>> entry : STATE_CITY_MAP.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(state.trim())) {
                return new ArrayList<>(entry.getValue());
            }
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isValidState(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        return STATE_CITY_MAP.keySet().stream()
                .anyMatch(s -> s.equalsIgnoreCase(state.trim()));
    }

    @Override
    public boolean isValidStateAndCity(String state, String city) {
        if (state == null || state.isBlank() || city == null || city.isBlank()) {
            return false;
        }
        List<String> cities = getCitiesByState(state);
        if (cities.isEmpty()) {
            return false;
        }
        return cities.stream().anyMatch(c -> c.equalsIgnoreCase(city.trim()));
    }
}
