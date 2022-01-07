package Model;

import Main.RunResults;
import Main.Settings;
import nl.tue.geometrycore.geometry.GeometryType;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.linear.Polygon;
import nl.tue.geometrycore.geometry.linear.Rectangle;
import nl.tue.geometrycore.geometry.mix.GeometryGroup;
import nl.tue.geometrycore.io.ReadItem;
import nl.tue.geometrycore.io.ipe.IPEReader;
import nl.tue.geometrycore.util.DoubleUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.awt.*;
import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;


public class CartogramModel {

    public static HashMap<Integer, Integer> lost_adj_for_drawing = new HashMap<>();
    public static HashMap<Integer, Integer> lost_adj_for_drawing_ILP = new HashMap<>();

    private class Block {
        Region r;
        int rankXplusY;
        int rankXminY;
    }

    private Map<Integer, String> layerNames;
    private Map<Integer, List<Region>> regions;
    //private HashMap<String, Rectangle> input_bounding_boxes;
    private HashMap<Integer, Vector> originalPositions;
    private HashMap<String, Integer> code_to_index;
    private HashMap<Integer, Region> index_to_region;
    private Map<Integer, Set<Integer>> stabilities;
    private HashMap<Integer, List<Integer>> adjacencies;
    private HashMap<Integer, HashMap<Integer, List<Integer>>> layer_adjacencies; // Note that adjacencies are global, some filtering for layers will be nedessary.
    private HashMap<Integer, HashMap<Integer, List<Integer>>> x_dominance_relation;
    private HashMap<Integer, HashMap<Integer, List<Integer>>> y_dominance_relation;
    private HashMap<Integer, List<Integer>> x_second_separation;
    private HashMap<Integer, List<Integer>> y_second_separation;
    private int layer_count;

    // A rectangle which spans all input locations of the cartogram areas. This can be used to estimate a scaling for the cplex variables, which measure distances in the cartogram.
    private Rectangle input_bounding_box;

    public CartogramModel(String topo_dir_path, String locs_path, String wght_path, String stab_path, String ipe_path, String bb_path, String norm_path) {
//        System.out.println(topo_dir_path);
//        System.out.println(locs_path);
//        System.out.println(wght_path);
//        System.out.println(stab_path);
//        System.out.println(ipe_path);
//        System.out.println(bb_path);
//        System.out.println(norm_path);
        try {
            //temp(ipe_path);
            createRegions(locs_path, wght_path, norm_path);
            loadAdjacencies(topo_dir_path);
            setNewObstacleWieghts();
            createStabilities(stab_path);
            createSeparationNeighboursLegacy();
            if(bb_path != null) loadSecondSeparation(bb_path);
            else if(ipe_path != null) readSecondSeparationFromIPEFile(ipe_path);
            else System.out.println("No method for deriving second separation provided");
            Vector offset = new Vector(input_bounding_box.leftBottom());
            offset.invert();
            input_bounding_box.translate(offset);
            //printComprehensiveReport();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setNewObstacleWieghts() {
        for (int i = 0; i < layer_count; i++) {
            for (Region region : regions.get(i)) {
                if (!region.code.startsWith("-")) continue;
                Set<Region> neighbours = getNeighbours(region, i);
                double avg_size = 0.0;
                for (Region neighbour : neighbours) {
                    avg_size += neighbour.getSize(i);
                }
                avg_size /= neighbours.size();
                region.setSize(i, avg_size);
            }
        }
    }

    public Rectangle getInputBoundingBox() { return input_bounding_box; }

    public Rectangle getCurrentBoundingBox() {
        Rectangle bb = new Rectangle();
        for (Region region : index_to_region.values()) {
            for (int i = 0; i < layer_count; i++) {
                if (region.isPresent(i)){
                    bb.includeGeometry(region.getRectangle(i));
                }
            }
        }
        return bb;
    }

    public List<Region> getRegions(Integer layer) {
        return regions.get(layer);
    }

    public Collection<Region> getRegions() {
        return index_to_region.values();
    }

    public Region getRegion(int index) {
        return index_to_region.get(index);
    }

    public boolean isPresent(Region r, int layer) {
        return regions.get(layer).contains(r);
    }

    public HashMap<Integer, Vector> getoriginalPositions(){ return originalPositions; }

    public boolean adjacent(Region r, Region s, Integer layer) {
        //if(regions.get(layer).contains(r) || )
        return layer_adjacencies.get(layer).get(r.index).contains(s.index);
    }

    public Set<Region> getNeighbours(Region r, int layer) {
        HashSet<Region> neighbours = new HashSet<>();
        for (Integer index : layer_adjacencies.get(layer).get(r.index)) {
            neighbours.add(index_to_region.get(index));
        }
        return neighbours;
    }

    /**
     * Returns the number of layers this layer tries stabilize itself to.
     * @param layer
     * @return
     */
    public int getNumberOfStableLayers(int layer) {
        return stabilities.get(layer).size();
    }

    public Set<Integer> getStableLayers(int layer) {
        return stabilities.get(layer);
    }


    public int getNumberOfStableRegions(int layer, Set<Integer> layers_in_program) {
        int counter = 0;
        for (Integer layer_b : stabilities.get(layer)) {
            if (!layers_in_program.contains(layer_b)) continue;
            for (Region r : regions.get(layer)) {
                if (!regions.get(layer_b).contains(r)) continue;
                counter += 1;
            }
        }
        return counter;
    }

    public int getNumberOfStableRegions(int layer) {
        int counter = 0;
        for (Integer layer_b : stabilities.get(layer)) {
            for (Region r : regions.get(layer)) {
                if (!regions.get(layer_b).contains(r)) continue;
                counter += 1;
            }
        }
        return counter;
    }

    public int getNumberOfAdjacentRegions(int layer) {
        int adj_regions = 0;
//        for (List<Integer> adj_region_list : layer_adjacencies.get(layer).values()) {
//            adj_regions += adj_region_list.size();
//        }

        for (Region region : regions.get(layer)) {
//            Set<Region> temp = getNeighbours(region, layer);
//            if (temp.contains(region)) {
//                System.out.println("IM MY OWN NEIGHBOUR");
//                temp.remove(region);
//            }
            adj_regions += getNeighbours(region, layer).size();
        }
        return adj_regions/2;
    }

    public boolean dominates(Region r, Region s, Integer layer) {
        return (dominates(r, s, layer, 'x') || dominates(r, s, layer, 'y'));
    }

    public boolean dominates(Region r, Region s, Integer layer, char dir) {

        switch(dir){
            case 'x':
                return x_dominance_relation.get(layer).get(r.index).contains(s.index);
            case 'y':
                return y_dominance_relation.get(layer).get(r.index).contains(s.index);
            default:
                System.err.println("The chosen direction " + dir + " is not recognized. Please choose either 'x' or 'y'.");
                return false;
        }
    }

    public boolean secondary_separated(Region r, Region s, char dir) {
        switch(dir){
            case 'x':
                return x_second_separation.get(r.index).contains(s.index);
            case 'y':
                return y_second_separation.get(r.index).contains(s.index);
            default:
                System.err.println("The chosen direction " + dir + " is not recognized. Please choose either 'x' or 'y'.");
                return false;
        }
    }

    public Map<Integer, Set<Integer>> getStabilities() {
        return stabilities;
    }

    public boolean isStableTo(int layer_from, int layer_to) {
        return stabilities.get(layer_from).contains(layer_to);
    }

    public String getLayername(Integer layer) {
        return layerNames.get(layer);
    }

    public int getLayerCount() {
        return layer_count;
    }

    public int getRegionCount() { return index_to_region.size(); }

    public int getRegionCount(int layer) { return regions.get(layer).size(); }

    private void createRegions(String locs_path, String wght_path, String norm_path) throws IOException {
        HashMap<String, Vector> locations = loadLocations(locs_path);
        HashMap<String, String> continents = null;
        HashMap<String, Color> cont_colors = new HashMap<>();
        if(Settings.colour_by_regions){
            continents = loadContinents(locs_path);
            int counter = 0;
            for (String s : continents.values()) {
                if(!cont_colors.containsKey(s)) {
                    cont_colors.put(s, Settings.colour_palette[counter]);
                    counter++;
                }
            }
        }

        layerNames = new HashMap<>();
        File file = new File(wght_path);
        BufferedReader br = new BufferedReader(new FileReader(file));

        // This saves the 'name', i.e., the year of the data for every layer. Not functionally important
        String[] year_tokens = br.readLine().replace("\n", "").split("\t");
        this.layer_count = year_tokens.length-2;
        stabilities = new HashMap<>();
        for (int i = 2; i < year_tokens.length; i++) {
            layerNames.put(i-2, year_tokens[i]);
            stabilities.put(i-2, new HashSet<>());
        }

        HashMap<String,double[]> weights_map = new HashMap<>();
        HashMap<Integer, HashMap<String, Double>> weights_per_layer = new HashMap<>();
        for (int i = 0; i < layer_count; i++) {
            weights_per_layer.put(i, new HashMap<>());
        }
        HashMap<String,String> name_map = new HashMap<>();
        HashMap<String,boolean[]> presence_map = new HashMap<>();
        String st;
        input_bounding_box = new Rectangle();

        while ((st = br.readLine()) != null){
            String[] tokens = st.split("\t");
            String code = tokens[0];
            String name = tokens[1];

            if(!locations.containsKey(code)) continue;

            input_bounding_box.includeGeometry(locations.get(code));

            double[] weights = new double[tokens.length-2];
            boolean[] presences = new boolean[tokens.length - 2];
            for (int i = 2; i < layer_count+2; i++) {
                presences[i-2] = isPresent(tokens[i]);
                if(isMissingValue(tokens[i]) || !presences[i-2]){
                    weights[i-2] = Double.NaN;
                    weights_per_layer.get(i-2).put(code, Double.NaN);
                } else {
                    weights[i-2] = Double.parseDouble(tokens[i]);
                    double wght = (Double.parseDouble(tokens[i]));
                    weights_per_layer.get(i-2).put(code, wght);
                }
            }
            presence_map.put(code, presences);
            weights_map.put(code, weights);
            name_map.put(code, name);
        }
        br.close();
        // Normalizing
        //double max_allowed = (input_bounding_box.width() + input_bounding_box.height())/(4*Math.sqrt(weights_map.size()));
        //double min_allowed = 5;
        //double max_allowed = (input_bounding_box.width() + input_bounding_box.height())/10;
        double max_allowed = (input_bounding_box.width() + input_bounding_box.height())/(1.5*Math.sqrt(weights_map.size()));
        double max_sum_allowed = 0.5*(input_bounding_box.areaUnsigned());
        //double max_allowed = (input_bounding_box.areaUnsigned())/(weights_map.size());
        double min_allowed = max_allowed*0.01;
        //double max_sum_allowed = ((max_allowed + min_allowed)/2) * weights_map.keySet().size();

        //Settings.LP.big_constant = (input_bounding_box.areaUnsigned()) * weights_map.keySet().size() * 4;
        //Settings.LP.big_constant = 10000;

        String norm_method = "sum";
        //int[] norm_map = new int[layer_count];
        HashMap<Integer, Set<Integer>> norm_map = new HashMap<>();
        if (!norm_path.equals("data/norm/")) {
            File file_norm = new File(norm_path);
            BufferedReader br_norm = new BufferedReader(new FileReader(file_norm));
            norm_method = br_norm.readLine().trim();
            String st_norm;
            while ((st_norm = br_norm.readLine()) != null) {
                String[] tok = st_norm.split("\t");
                int set = Integer.parseInt(tok[1]);
                int layer = Integer.parseInt(tok[0]);
                if(!norm_map.containsKey(set)) {
                    norm_map.put(set, new HashSet<>());
                }
                norm_map.get(set).add(layer);
            }
        } else {
            HashSet<Integer> dummy_all = new HashSet<>();
            for (int i = 0; i < layer_count; i++) {
                dummy_all.add(i);
            }
            norm_map.put(0, dummy_all);
        }

        double[] layer_max = new double[layer_count];
        double[] layer_min = new double[layer_count];
        double[] layer_sum = new double[layer_count];

        for (int layer = 0; layer < layer_count; layer++) {
            HashMap<String, Double> layer_weights = weights_per_layer.get(layer);
            layer_min[layer] = layer_weights.values().stream().filter(aDouble -> !Double.isNaN(aDouble)).min(Double::compareTo).get();
            layer_max[layer] = layer_weights.values().stream().filter(aDouble -> !Double.isNaN(aDouble)).max(Double::compare).get();
            layer_sum[layer] = layer_weights.values().stream().filter(aDouble -> !Double.isNaN(aDouble)).mapToDouble(Double::doubleValue).sum();
        }

        switch(norm_method) {
            case "sum":
                for (Set<Integer> norm_set : norm_map.values()) {
                    double biggest_sum = 0;
                    for (Integer layer : norm_set) {
                        if (layer_sum[layer] > biggest_sum) biggest_sum = layer_sum[layer];
                    }
                    for (Integer layer : norm_set) {
                        for (String code : weights_per_layer.get(layer).keySet()) {
                            weights_map.get(code)[layer] = (weights_per_layer.get(layer).get(code) * (max_sum_allowed/biggest_sum));
                        }
                    }
                }
                break;
            case "max":
                for (Set<Integer> norm_set : norm_map.values()) {
                    double biggest_max = 0;
                    double smallest_min = Double.MAX_VALUE;
                    for (Integer layer : norm_set) {
                        if (layer_max[layer] > biggest_max) biggest_max = layer_max[layer];
                        if (layer_min[layer] < smallest_min) smallest_min = layer_min[layer];
                    }
                    for (Integer layer : norm_set) {
                        for (String code : weights_per_layer.get(layer).keySet()) {
                            weights_map.get(code)[layer] = (norm(weights_per_layer.get(layer).get(code), smallest_min, biggest_max, max_allowed, min_allowed));
                        }
                    }
                }
                break;
        }

        //OLD Normalization
//        double total_min = Double.MAX_VALUE, total_max = -Double.MAX_VALUE;
//        for (int layer = 0; layer < layer_count; layer++) {
//            HashMap<String, Double> layer_weights = weights_per_layer.get(layer);
//            double min = layer_weights.values().stream().filter(aDouble -> !Double.isNaN(aDouble)).min(Double::compare).get();
//            double max = layer_weights.values().stream().filter(aDouble -> !Double.isNaN(aDouble)).max(Double::compare).get();
//            if (collective_normalization_flag) {
//                if(min < total_min) total_min = min;
//                if(max > total_max) total_max = max;
//            } else {
//                for (String code : layer_weights.keySet()) {
//                    weights_map.get(code)[layer] = norm(layer_weights.get(code), min, max, max_allowed, min_allowed);
//                }
//            }
//        }
//
//        if(collective_normalization_flag) {
//            for (int layer = 0; layer < layer_count; layer++) {
//                for (String code : weights_per_layer.get(layer).keySet()) {
//                    weights_map.get(code)[layer] = norm(weights_per_layer.get(layer).get(code), total_min, total_max, max_allowed, min_allowed);
//                }
//            }
//        }

        regions = new HashMap<>();
        code_to_index = new HashMap<>();
        index_to_region = new HashMap<>();
        originalPositions = new HashMap<>();
        // Setting the right coordinates
        for (String key : locations.keySet()) {
            locations.put(key,Vector.subtract(locations.get(key), input_bounding_box.leftBottom()));
        }

        double bb_w = input_bounding_box.width();
        double bb_h = input_bounding_box.height();
//        CIELab cieLab = new CIELab();
//        ColorSpace CIEXYZ = ColorSpace.getInstance(ColorSpace.CS_sRGB);
////        float[] test = cieLab.toCIEXYZ(new float[]{127, 127, 127});
////        float[] test = cieLab.fromRGB(new float[]{255, 0, 0});
//        float[] test = cielabTOxyz(new float[]{50.0f, 100, 100});
//        test = CIEXYZ.fromCIEXYZ(test);
//        System.out.println(test[0] + ", " + test[1] + ", " + test[2]);
//        Color testColor = new Color(test[0], test[1], test[2]);
//        System.out.println(testColor);


        int index = 0;
        for (String code : weights_map.keySet()) {
            originalPositions.put(index, locations.get(code));

            //compute colour based on position of the centroid in input bounding box
            //using a bivariate color grading
            Color color = null;
            if(Settings.colour_by_regions){
                color = cont_colors.get(continents.get(code));
            } else {
                color = new Color(1 - ((float) (locations.get(code).getX()/(2*bb_w)) + (float) (locations.get(code).getY()/(2*bb_h))), (float) (locations.get(code).getX()/bb_w), (float) (locations.get(code).getY()/bb_h));
            }

            Region r = new Region(index, code, locations.get(code), color, weights_map.get(code), name_map.get(code));
            if(code.startsWith("-")) r.setAsObstacle();
            for (int i = 0; i < layer_count; i++) {
                if(presence_map.get(code)[i]) {
                    regions.computeIfAbsent(i, k -> new ArrayList<>());
                    regions.get(i).add(r);

                    // THIS IS REDUNDANT INFORMATION SHOULD BE HANDLED IN CARTOGRAM MODEL
                    r.setPresent(i);
                }
            }
            code_to_index.put(code, index);
            index_to_region.put(index, r);
            //System.out.println(r);
            index += 1;
        }
    }

    private HashMap<String, String> loadContinents(String locs_path) throws IOException {
        File file = new File(locs_path);
        BufferedReader br = new BufferedReader(new FileReader(file));
        String st;
        HashMap<String, String> continents = new HashMap<>();
        br.readLine();
        while ((st = br.readLine()) != null){
            String[] tokens = st.split("\t");
            continents.put(tokens[0], tokens[3]);
        }
        br.close();
        return continents;
    }

    private float[] cielabTOxyz(float[] values) {
        float L = values[0];
        float a = values[1];
        float b = values[2];


        float var_Y = (L + 16) / 116.0f;
        float var_X = a / 500.0f + var_Y;
        float var_Z = var_Y - b / 200.0f;

        if ( Math.pow(var_Y,3)  > 0.008856 ){
            var_Y = (float) Math.pow(var_Y,3);
        } else {
            var_Y = ( var_Y - 16.0f / 116.0f ) / 7.787f;
        }
        if ( Math.pow(var_X,3)  > 0.008856 ) {
            var_X = (float) Math.pow(var_X,3);
        } else {
            var_X = ( var_X - 16.0f / 116.0f ) / 7.787f;
        }
        if ( Math.pow(var_Z,3)  > 0.008856 ) {
            var_Z = (float) Math.pow(var_Z,3);
        } else{
            var_Z = ( var_Z - 16.0f / 116.0f ) / 7.787f;
        }

        return new float[] { var_X * 0.950489f, var_Y * 1.0f, var_Z * 1.088840f };
    }

    private double norm(double value, double min, double max, double max_allowed, double min_allowed) {
        if(Double.isNaN(value)) return Double.NaN;
        if(value == min) return min_allowed;
        return min_allowed + ((max_allowed-min_allowed) * ((value - min)/(max-min)));
    }

    private HashMap<String, Vector> loadLocations(String locs_path) throws IOException {
        File file = new File(locs_path);
        BufferedReader br = new BufferedReader(new FileReader(file));
        String st;
        HashMap<String, Vector> locations = new HashMap<>();
        br.readLine();
        while ((st = br.readLine()) != null){
            String[] tokens = st.split("\t");
            locations.put(tokens[0], new Vector(Double.parseDouble(tokens[1]), Double.parseDouble(tokens[2])));
        }
        br.close();
        return locations;
    }

    private void loadAdjacencies(String topo_file_path) throws IOException {

        adjacencies = new HashMap<>();
        BufferedReader br = new BufferedReader(new FileReader(topo_file_path));
        String st;
        while ((st = br.readLine()) != null){
            String[] tokens = st.split("\t");
            if(!(code_to_index.containsKey(tokens[0])&&code_to_index.containsKey(tokens[1]))) continue;
            int index_1 = code_to_index.get(tokens[0]);
            int index_2 = code_to_index.get(tokens[1]);
            if(index_1 == index_2) continue;
            if (adjacencies.containsKey(index_1)) {
                adjacencies.get(index_1).add(index_2);
            } else {
                adjacencies.put(index_1, new ArrayList<Integer>() {{ add(index_2); }});
            }
            if (adjacencies.containsKey(index_2)) {
                adjacencies.get(index_2).add(index_1);
            } else {
                adjacencies.put(index_2, new ArrayList<Integer>() {{ add(index_1); }});
            }
        }
        br.close();

        layer_adjacencies = new HashMap<>();
        for (int layer = 0; layer < layer_count; layer++) {
            HashMap<Integer, List<Integer>> current_layer_adjacencies = new HashMap<>();
            for (Region r : regions.get(layer)) {
                if(!regions.get(layer).contains(r)) continue;
                List<Integer> region_layer_neighbours = new ArrayList<>();
                if(adjacencies.get(r.index) != null){
                    for (Integer neighbour_index : adjacencies.get(r.index)) {
                        Region neighbour = index_to_region.get(neighbour_index);
                        if(regions.get(layer).contains(neighbour)){
                            region_layer_neighbours.add(neighbour_index);
                        }
                    }
                }
                current_layer_adjacencies.put(r.index, region_layer_neighbours);
            }
            layer_adjacencies.put(layer, current_layer_adjacencies);
        }
    }

    private void createStabilities(String stab_path) throws IOException {
        File file = new File(stab_path);
        BufferedReader br = new BufferedReader(new FileReader(file));
        String st;
        //System.out.println(stabilities.keySet());

        br.readLine();
        while ((st = br.readLine()) != null){
            String[] tokens = st.split("\t");
            //System.out.println(st);
            stabilities.get(Integer.parseInt(tokens[1])).add(Integer.parseInt(tokens[0]));
        }
        br.close();
    }

    /**
     * This method determines if the read value for a region is interpreted as a missing value
     * @param readWeight
     * @return
     */
    private boolean isMissingValue(String readWeight) {
        boolean missing = false;
        if(new HashSet<String>(){{
                                    add("nan");
                                    add("");
                                    add("-999999999");
                                }}.contains(readWeight.toLowerCase())){
            missing = true;
        }
        return missing;
    }

    /**
     * This method determines if the read value for a region is interpreted as being present in a layer
     * @param readWeight
     * @return
     */
    private boolean isPresent(String readWeight) {
        return !readWeight.equals("null");
    }

    public void printComprehensiveReport() throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter("./outputs/model_report.txt"));

        bw.write("The layer count is: " + layer_count); bw.newLine();
        bw.write("The layer names are:"); bw.newLine();
        for (Map.Entry<Integer, String> entry : layerNames.entrySet()) {
            bw.write("\t" + entry.getKey() +": " + entry.getValue());
            bw.newLine();
        }
        bw.newLine();

        bw.write("The region are:"); bw.newLine();
        for (Map.Entry<Integer, List<Region>> entry : regions.entrySet()) {
            bw.write("On layer " + entry.getKey() + " there are " + entry.getValue().size() + " regions:");
            bw.newLine();
            for (Region region : entry.getValue()) {
                bw.write(region.toString());
                bw.newLine();
            }
        }
        bw.newLine();
        bw.write("According to bookkeeping in 'code_to_index' and 'index_to_region' the following should all be equal"); bw.newLine();
        for (Map.Entry<String, Integer> code_entry : code_to_index.entrySet()) {
            bw.write(code_entry.getKey() + " <-> " + index_to_region.get(code_entry.getValue()).code); bw.newLine();
        }
        bw.newLine();
        bw.write("These are the read stabilities:"); bw.newLine();
        for (Map.Entry<Integer, Set<Integer>> stab_entry : stabilities.entrySet()) {
            bw.write(stab_entry.getKey() + " is stable to the following layers " + Arrays.toString(stab_entry.getValue().toArray())); bw.newLine();
        }
        bw.newLine();
        bw.write("These are the read adjacencies (global):"); bw.newLine();
        for (Map.Entry<Integer, List<Integer>> adj_entry : adjacencies.entrySet()) {
            bw.write("The region " + index_to_region.get(adj_entry.getKey()).code + " (index " +adj_entry.getKey() + ") is adjacent to: " + adj_entry.getValue().stream().map(integer -> index_to_region.get(integer).code).collect(Collectors.toList())); bw.newLine();
        }
        bw.newLine();
        bw.write("These are the read adjacencies (layerwise):"); bw.newLine();
        for (Map.Entry<Integer, HashMap<Integer, List<Integer>>> layer_adj_entry : layer_adjacencies.entrySet()) {
            bw.write("For layer " + layer_adj_entry.getKey() + ":"); bw.newLine();
            for (Map.Entry<Integer, List<Integer>> layer_region_adj_entry : layer_adj_entry.getValue().entrySet()) {
                bw.write("The region " + index_to_region.get(layer_region_adj_entry.getKey()).code + " (index " +layer_region_adj_entry.getKey() + ") is adjacent to: " + Arrays.toString(layer_region_adj_entry.getValue().toArray())); bw.newLine();

            }
            bw.newLine();
        }
        bw.newLine();
        bw.write("These are the computed x-dominances (layerwise):"); bw.newLine();
        for (Map.Entry<Integer, HashMap<Integer, List<Integer>>> layer_x : x_dominance_relation.entrySet()) {
            bw.write("For layer " + layer_x.getKey() + ":"); bw.newLine();
            for (Map.Entry<Integer, List<Integer>> layer_region_adj_entry : layer_x.getValue().entrySet()) {
                bw.write("The region " + index_to_region.get(layer_region_adj_entry.getKey()).code + "(index " +layer_region_adj_entry.getKey() + ") x-dominates: " + Arrays.toString(layer_region_adj_entry.getValue().toArray())); bw.newLine();
            }
            bw.newLine();
        }
        bw.newLine();
        bw.write("These are the computed y-dominances (layerwise):"); bw.newLine();
        for (Map.Entry<Integer, HashMap<Integer, List<Integer>>> layer_y : y_dominance_relation.entrySet()) {
            bw.write("For layer " + layer_y.getKey() + ":"); bw.newLine();
            for (Map.Entry<Integer, List<Integer>> layer_region_adj_entry : layer_y.getValue().entrySet()) {
                bw.write("The region " + index_to_region.get(layer_region_adj_entry.getKey()).code + "(index " +layer_region_adj_entry.getKey() + ") y-dominates: " + Arrays.toString(layer_region_adj_entry.getValue().toArray())); bw.newLine();
            }
            bw.newLine();
        }
        bw.close();
    }

    /**
     * Creates a file containing the created cartograms. Locations and sizes of all regions are saved.
     * However scaling of obstacles can not necessarily be reconstructed (only actual size per layer is saved).
     * @param file_path the output file path
     * @return The entire file as a string
     */
    public void writeRegionReportToFile(String file_path) throws IOException {

        System.out.println("Writing regions\n\tto " + file_path);

        JSONArray region_report = new JSONArray();
        for (Map.Entry<Integer, Region> entry : index_to_region.entrySet()) {
            Region r = entry.getValue();
            JSONObject region_j = new JSONObject();
            region_j.put("Index", entry.getKey());
            region_j.put("Name", r.name);
            region_j.put("Code", r.code);
            region_j.put("Obstacle?", r.isObstacle);
            JSONArray layer_entries_j = new JSONArray();
            for (int i = 0; i < layer_count; i++) {
                if(!r.isPresent(i)) {
                    layer_entries_j.add("Absent");
                } else {
                    JSONObject layer_entry = new JSONObject();
                    layer_entry.put("loc", r.getPosition(i).toString());
                    layer_entry.put("size", r.getSize(i));
                    layer_entries_j.add(layer_entry);
                }
            }
            region_j.put("Layer Entries", layer_entries_j);
            region_report.add(region_j);
        }
        File f = new File(file_path);
        f.getParentFile().mkdirs();
        BufferedWriter bw = new BufferedWriter(new FileWriter(f));
        bw.write(region_report.toJSONString());
        bw.close();
    }



    public void evaluateToFile(String file_path, RunResults result, boolean round) throws IOException {

        System.out.println("Evaluating\n\tto " + file_path);

        System.out.println("Starting Eval");
        System.out.println("\tCalculating Lost Adjacencies...");
        HashMap<Integer, Integer> lost_adjacencies;
        HashMap<Integer, Integer> lost_adjacencies_ILP = null;
        if (Settings.solution_method.equals("LP")){
            lost_adjacencies = Evaluation.calculateLostAdjacencies(this);
            lost_adjacencies_ILP = (HashMap<Integer, Integer>) result.getLostAdjacencies();
        } else {
            lost_adjacencies = Evaluation.calculateLostAdjacencies(this);
        }
        lost_adj_for_drawing = lost_adjacencies;
        lost_adj_for_drawing_ILP = lost_adjacencies_ILP;
//        System.out.println((lost_adjacencies_ILP.get(0).equals(lost_adjacencies.get(0)) ? "ITS":"NOT") + " correct: " + lost_adjacencies_ILP.get(0) + " compared to " + lost_adjacencies.get(0));
//        System.out.println((lost_adjacencies_ILP.get(1).equals(lost_adjacencies.get(1)) ? "ITS":"NOT") + " correct: " + lost_adjacencies_ILP.get(1) + " compared to " + lost_adjacencies.get(1));
//        System.out.println((lost_adjacencies_ILP.get(2).equals(lost_adjacencies.get(2)) ? "ITS":"NOT") + " correct: " + lost_adjacencies_ILP.get(2) + " compared to " + lost_adjacencies.get(2));
//        System.out.println((lost_adjacencies_ILP.get(3).equals(lost_adjacencies.get(3)) ? "ITS":"NOT") + " correct: " + lost_adjacencies_ILP.get(3) + " compared to " + lost_adjacencies.get(3));
        System.out.println("\tCalculating Adjacency Distances...");
        HashMap<Integer, Double> adjacency_distances = Evaluation.calculateDistanceForAdjacency(this);
        System.out.println("\tCalculating Origin Displacement...");
        HashMap<Integer, HashMap<String, Double>> origin_displacement = Evaluation.computeOriginDisplacement(this);
        System.out.println("\tCalculating Inter Layer Displacement...");
        HashMap<Integer, HashMap<Integer, HashMap<String, Double>>> inter_layer_displacement = Evaluation.computeInterLayerDisplacement(this);
        System.out.println("\tCalculating Inter Layer Stability...");
        HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, Double>>>> stability_metric = Evaluation.calculateStabilityInterLayer(this);
        System.out.println("\tCalculating Input Stability...");
        HashMap<Integer, HashMap<String, HashMap<String, Double>>> stability_input_metric = Evaluation.calculateStabilitytoInput(this);
        System.out.println("\tWriting to file");


        File f = new File(file_path);
        f.getParentFile().mkdirs();
        BufferedWriter bw = new BufferedWriter(new FileWriter(f));

        //0
        bw.write("{");
        bw.write("\"Settings\":");
        //1
        bw.write("{");
        bw.write("\"Method\":\"" + Settings.solution_method + "\",");

//        JSONObject runLog = new JSONObject();
//        JSONObject settings = new JSONObject();
//        JSONObject constraints = new JSONObject();
//        JSONObject metrics = new JSONObject();
//        settings.put("Method", Settings.solution_method);
        boolean comma = false;
        switch(Settings.solution_method){

            case "LP":
                bw.write("\"Constraints\":");
                //2
                bw.write("{");
                boolean ILP = false;
                if(Settings.LP.disjoint_ilp_c) {
                    bw.write("\"C0\":" + true);
//                    constraints.put("C0", true);
                    comma = true;
                    ILP = true;
                }
                if(Settings.LP.disjoint_c) {
                    bw.write((comma?",":"") +"\"C1\":" + true);
//                    constraints.put("C1", true);
                    comma = true;
                }
                if(Settings.LP.double_disjont_c) {
                    bw.write((comma?",":"") +"\"C2\":" + true);
//                    constraints.put("C2", true);
                    comma = true;
                }
                if(Settings.LP.distance_minimization_c) {
                    bw.write((comma?",":"") +"\"C3\":" + Settings.LP.factor_dist_min);
//                    constraints.put("C3", Settings.LP.factor_dist_min);
                    comma = true;
                }
                if(Settings.LP.angle_nuance_c) {
                    bw.write((comma?",":"") +"\"C4\":" + Settings.LP.factor_angle_nuance);
//                    constraints.put("C4", Settings.LP.factor_angle_nuance);
                    comma = true;
                }
                if(Settings.LP.topology_c) {
                    bw.write((comma?",":"") +"\"C5\":" + Settings.LP.factor_topology);
//                    constraints.put("C5", Settings.LP.factor_topology);
                    comma = true;
                    ILP = true;
                }
                if(Settings.LP.origin_displacement_c) {
                    bw.write((comma?",":"") +"\"C6\":" + Settings.LP.factor_orig_displ);
//                    constraints.put("C6", Settings.LP.factor_orig_displ);
                    comma = true;
                }
                if(Settings.LP.stability_c) {
                    bw.write((comma?",":"") +"\"C7\":" + Settings.LP.factor_stability);
//                    constraints.put("C7", Settings.LP.factor_stability);
                }
                //2
                bw.write("},");
                bw.write("\"MIP\":" + ILP + ",");
//                settings.put("ILP", ILP);

                bw.write("\"MIP Warmstart\":" + Settings.LP.mip_warmstart + ",");
//                settings.put("MIP Warmstart", Settings.LP.mip_warmstart);

                bw.write("\"Threads\":" + Settings.LP.THREADS + ",");
//                settings.put("Threads", Settings.LP.THREADS);

                bw.write("\"L_inf\":" + Settings.LP.L_infinity + ",");
//                settings.put("L_inf", Settings.LP.L_infinity);

                bw.write("\"min_contact_factor\":" + Settings.LP.min_contact_factor + ",");
//                settings.put("min_contact_factor", Settings.LP.min_contact_factor);

                if(Settings.LP.angle_nuance_c) bw.write("\"nuance_non_adjacent_multiplier\":" + Settings.LP.nuance_non_adjacent_multiplier + ",");
//                if(Settings.LP.angle_nuance_c) settings.put("nuance_non_adjacent_multiplier", Settings.LP.nuance_non_adjacent_multiplier);

                bw.write("\"obstacle_lower_bound\":" + Settings.LP.obstacle_lower_bound + ",");
//                settings.put("obstacle_lower_bound", Settings.LP.obstacle_lower_bound);

                bw.write("\"obstacle_upper_bound\":" + Settings.LP.obstacle_upper_bound);
//                settings.put("obstacle_upper_bound", Settings.LP.obstacle_upper_bound);

                //if(Settings.LP.topology_c) settings.put("big_constant", Settings.LP.big_constant);
                break;
            case "Force":
                bw.write("\"Constraints\":");
                //2
                bw.write("{");
                if(Settings.Force.disjoint_f) {
                    bw.write("\"F1\":" + Settings.Force.disjoint_weight);
//                    constraints.put("F1", Settings.Force.disjoint_weight);
                }
                if(Settings.Force.origin_f) {
                    bw.write(",\"F2\":" + Settings.Force.origin_weight);
//                    constraints.put("F2", Settings.Force.origin_weight);
                }
                if(Settings.Force.topological_f) {
                    bw.write(",\"F3\":" + Settings.Force.topological_weight);
//                    constraints.put("F3", Settings.Force.topological_weight);
                }
                if(Settings.Force.stability_f) {
                    bw.write(",\"F4\":" + Settings.Force.stability_weight);
//                    constraints.put("F4", Settings.Force.stability_weight);
                }

                //2
                bw.write("},");

                bw.write("\"max_iterations\":" + Settings.Force.max_iterations + ",");
//                settings.put("max_iterations", Settings.Force.max_iterations);

                bw.write("\"min_movement\":" + Settings.Force.minimal_movement + ",");
//                settings.put("min_movement", Settings.Force.minimal_movement);

                bw.write("\"speed_up_method\":\"" + Settings.Force.speed_up + "\",");
//                settings.put("speed_up_method", Settings.Force.speed_up);

                bw.write("\"cooling_factor\":" + Settings.Force.cooling_factor);
//                settings.put("cooling_factor", Settings.Force.cooling_factor);
                break;
        }
//        settings.put("Constraints", constraints);
//        runLog.put("Settings", settings);

        //1
        bw.write("},");
        System.out.println("\t\tSettings written to file");


//        JSONObject lost_adj_json = new JSONObject();
//        JSONObject adj_dist_json = new JSONObject();
//        JSONObject orig_displ_json = new JSONObject();
//        JSONObject inter_layer_json = new JSONObject();
//        JSONObject stability_json = new JSONObject();
//        JSONObject stability_input_json = new JSONObject();



        bw.write("\"Measures\":");
        //1
        bw.write("{");
        bw.write("\"lost_adjacencies\":");
        //2
        bw.write("{");

        comma = false;
        for (Map.Entry<Integer, Integer> integerIntegerEntry : lost_adjacencies.entrySet()) {
            bw.write((comma?",":"") + "\"" + integerIntegerEntry.getKey() + "\":" + integerIntegerEntry.getValue());
            comma = true;
        }
//        lost_adj_json.putAll(lost_adjacencies);

        //2
        bw.write("},");
        bw.write("\"adjacency_distances\":");
        //2
        bw.write("{");

        comma = false;
        for (Map.Entry<Integer, Double> integerDoubleEntry : adjacency_distances.entrySet()) {
            Double value = integerDoubleEntry.getValue();
            if(round) {
                value = Math.round(value * 100.0) / 100.0;
            }
            bw.write((comma?",":"") + "\"" + integerDoubleEntry.getKey() + "\":" + value);
            comma = true;
        }
//        adj_dist_json.putAll(adjacency_distances);
        //2
        bw.write("},");

        bw.write("\"origin_displacement\":");
        //2
        bw.write("{");

        comma = false;
        for (Map.Entry<Integer, HashMap<String, Double>> entry : origin_displacement.entrySet()) {
            bw.write((comma?",":"")+"\""+entry.getKey()+"\":");
            //3
            bw.write("{");
            boolean comma1 = false;
            for (Map.Entry<String, Double> inner_entry : entry.getValue().entrySet()) {

                Double value = inner_entry.getValue();
                if(round) {
                    value = Math.round(value * 100.0) / 100.0;
                }
                bw.write((comma1?",":"")+"\""+inner_entry.getKey()+"\":"+value);
                comma1 = true;
            }
            //3
            bw.write("}");
            comma = true;
        }
//        orig_displ_json.putAll(origin_displacement);
        //2
        bw.write("},");



        //lost_adj_json.forEach((o, o2) -> System.out.println(o + ": " +o2));


        bw.write("\"inter_layer_displacement\":");
        //2
        bw.write("{");

        comma = false;
        for (Map.Entry<Integer, HashMap<Integer, HashMap<String, Double>>> first_entry : inter_layer_displacement.entrySet()) {
            for (Map.Entry<Integer, HashMap<String, Double>> second_entry : first_entry.getValue().entrySet()) {
                bw.write((comma?",":"")+"\""+first_entry.getKey()+"-"+second_entry.getKey()+"\":");
                //3
                bw.write("{");
                boolean comma1 = false;
                for (Map.Entry<String, Double> inner_entry : second_entry.getValue().entrySet()) {

                    Double value = inner_entry.getValue();
                    if(round) {
                        value = Math.round(value * 100.0) / 100.0;
                    }
                    bw.write((comma1?",":"")+"\""+inner_entry.getKey()+"\":"+value);
                    comma1 = true;
                }
                //3
                bw.write("}");
                comma = true;
            }
        }
        //2
        bw.write("},");


//        for (Map.Entry<Integer, HashMap<Integer, HashMap<String, Double>>> first_entry : inter_layer_displacement.entrySet()) {
//            for (Map.Entry<Integer, HashMap<String, Double>> second_entry : first_entry.getValue().entrySet()) {
//                String handle = first_entry.getKey() + ";" +second_entry.getKey();
//                JSONObject pair_entry = new JSONObject();
//                pair_entry.putAll(second_entry.getValue());
//
//
//                inter_layer_json.put(handle, pair_entry);
//            }
//        }

        bw.write("\"stability\":");
        //2
        bw.write("{");

        comma = false;
        for (Map.Entry<Integer, HashMap<Integer, HashMap<String, HashMap<String, Double>>>> first_entry : stability_metric.entrySet()) {
            for (Map.Entry<Integer, HashMap<String, HashMap<String, Double>>> second_entry : first_entry.getValue().entrySet()) {
                bw.write((comma?",":"")+"\""+first_entry.getKey()+"-"+second_entry.getKey()+"\":");
                //3
                bw.write("{");
                boolean comma1 = false;
                for (Map.Entry<String, HashMap<String, Double>> third_entry : second_entry.getValue().entrySet()) {
                    for (Map.Entry<String, Double> fourth_entry : third_entry.getValue().entrySet()) {

                        Double value = fourth_entry.getValue();
                        if(round) {
                            value = Math.round(value * 100.0) / 100.0;
                        }
                        bw.write((comma1?",":"")+"\""+third_entry.getKey()+"-"+fourth_entry.getKey()+"\":"+value);
                        comma1 = true;
                    }
                }
                //3
                bw.write("}");
                comma = true;
            }
        }
        //2
        bw.write("},");

//        for (Map.Entry<Integer, HashMap<Integer, HashMap<String, HashMap<String, Double>>>> first_entry : stability_metric.entrySet()) {
//            for (Map.Entry<Integer, HashMap<String, HashMap<String, Double>>> second_entry : first_entry.getValue().entrySet()) {
//                String layer_handle = first_entry.getKey() + ";" +second_entry.getKey();
//                JSONObject pair_entry = new JSONObject();
//
//                for (Map.Entry<String, HashMap<String, Double>> third_entry : second_entry.getValue().entrySet()) {
//                    for (Map.Entry<String, Double> fourth_entry : third_entry.getValue().entrySet()) {
//                        String region_handle = third_entry.getKey() + ";" + fourth_entry.getKey();
//                        pair_entry.put(region_handle, fourth_entry.getValue());
//                    }
//                }
//                stability_json.put(layer_handle, pair_entry);
//            }
//        }

        bw.write("\"stability_input\":");
        //2
        bw.write("{");

        comma = false;
        for (Map.Entry<Integer, HashMap<String, HashMap<String, Double>>> first_entry : stability_input_metric.entrySet()) {
            for (Map.Entry<String, HashMap<String, Double>> second_entry : first_entry.getValue().entrySet()) {
                bw.write((comma?",":"")+"\""+first_entry.getKey()+"-"+second_entry.getKey()+"\":");
                //3
                bw.write("{");
                boolean comma1 = false;
                for (Map.Entry<String, Double> inner_entry : second_entry.getValue().entrySet()) {

                    Double value = inner_entry.getValue();
                    if(round) {
                        value = Math.round(value * 100.0) / 100.0;
                    }
                    bw.write((comma1?",":"")+"\""+inner_entry.getKey()+"\":"+value);
                    comma1 = true;
                }
                //3
                bw.write("}");
                comma = true;
            }
        }
        //2
        bw.write("},");

//        for (Map.Entry<Integer, HashMap<String, HashMap<String, Double>>> entry : stability_input_metric.entrySet()) {
//            String layer_handle = entry.getKey().toString();
//            JSONObject pair_entry = new JSONObject();
//
//            for (Map.Entry<String, HashMap<String, Double>> third_entry : entry.getValue().entrySet()) {
//                for (Map.Entry<String, Double> fourth_entry : third_entry.getValue().entrySet()) {
//                    String region_handle = third_entry.getKey() + ";" + fourth_entry.getKey();
//                    pair_entry.put(region_handle, fourth_entry.getValue());
//                }
//            }
//            stability_input_json.put(layer_handle, pair_entry);
//        }



        List<Double> runtimes = result.getRuntimes();
        bw.write("\"runtimes\":");
        //2
        bw.write("{");
        comma = false;
        for (int program_number = 0; program_number < runtimes.size(); program_number++) {
            bw.write((comma?",":"")+"\"" + program_number + "\":" + runtimes.get(program_number));
            comma = true;
        }
//        adj_dist_json.putAll(adjacency_distances);
        //2
        bw.write("},");


        List<Integer> iterations = result.getIterations();
        bw.write("\"iterations\":");
        //2
        bw.write("{");
        comma = false;
        for (int program_number = 0; program_number < iterations.size(); program_number++) {
            bw.write((comma?",":"")+"\"" + program_number + "\":" + iterations.get(program_number));
            comma = true;
        }
//        adj_dist_json.putAll(adjacency_distances);
        //2
        bw.write("}");


//        JSONObject runtime_json = new JSONObject();
//        List<Double> runtimes = result.getRuntimes();
//        for (int program_number = 0; program_number < runtimes.size(); program_number++) {
//            runtime_json.put(program_number, runtimes.get(program_number));
//        }

//        JSONObject iteration_json = new JSONObject();
//        List<Integer> iterations = result.getIterations();
//        for (int program_number = 0; program_number < iterations.size(); program_number++) {
//            iteration_json.put(program_number, iterations.get(program_number));
//        }

//        metrics.put("runtimes", iteration_json);
//        metrics.put("iterations", iteration_json);
//        metrics.put("lost_adjacencies", lost_adj_json);
//        metrics.put("adjacency_distances", adj_dist_json);
//        metrics.put("origin_displacements", orig_displ_json);
//        metrics.put("region_movement", inter_layer_json);
//        metrics.put("stability", stability_json);
//        metrics.put("stability_to_input", stability_input_json);
//        runLog.put("Metrics", metrics);

        //1
        bw.write("}");

//        System.out.println(file_path);
//
//        File f = new File(file_path);
//        f.getParentFile().mkdirs();
//        BufferedWriter bw = new BufferedWriter(new FileWriter(f));
//        bw.write(runLog.toJSONString());


        //0
        bw.write("}");

        bw.close();
    }

    public void updateRegionPositions(int layer, Map<Integer, Vector> layer_positions) {
        for (Map.Entry<Integer, Vector> entry : layer_positions.entrySet()) {
            Region r = index_to_region.get(entry.getKey());
            r.setPosition(layer, entry.getValue());
        }
    }

    public void updateObstacleSizes(int layer, Map<Integer, Double> layer_positions) {
        for (Map.Entry<Integer, Double> entry : layer_positions.entrySet()) {
            Region r = index_to_region.get(entry.getKey());
            r.setSize(layer, entry.getValue());
        }
    }

    public double getMinSize(){
        double min = Double.MAX_VALUE;
        for (int layer = 0; layer < layer_count; layer++) {
            for (Region region : regions.get(layer)) {
                if(region.getSize(layer) < min){
                    min = region.getSize(layer);
                }
            }
        }
        return min;
    }

    public double getMinSize(int layer){
        double min = Double.MAX_VALUE;
        for (Region region : regions.get(layer)) {
            if(region.getSize(layer) < min){
                min = region.getSize(layer);
            }
        }
        return min;
    }

    public double getMaxSize(){
        double max = 0;
        for (int layer = 0; layer < layer_count; layer++) {
            for (Region region : regions.get(layer)) {
                if(region.getSize(layer) > max){
                    max = region.getSize(layer);
                }
            }
        }
        return max;
    }

    public double getMaxSize(int layer){
        double max = 0;
        for (Region region : regions.get(layer)) {
            if(region.getSize(layer) > max){
                max = region.getSize(layer);
            }
        }
        return max;
    }

    public double getAverageSize(){
        double avg = 0;
        double number = 0;
        for (int layer = 0; layer < layer_count; layer++) {
            number += regions.get(layer).size();
            for (Region region : regions.get(layer)) {
                avg += region.getSize(layer);
            }
        }
        return avg/number;
    }

    public double getAverageSize(int layer){
        double avg = 0;
        for (Region region : regions.get(layer)) {
            avg += region.getSize(layer);
        }
        return avg/regions.get(layer).size();
    }

    private void createSeparationNeighboursLegacy() {
        x_dominance_relation = new HashMap<>();
        y_dominance_relation = new HashMap<>();
        for (int layer = 0; layer < layer_count; layer++) {
            List<Region> layer_regions = regions.get(layer);

            int n = layer_regions.size();

            HashMap<Integer, List<Integer>> layer_x_dominance = new HashMap<>();
            HashMap<Integer, List<Integer>> layer_y_dominance = new HashMap<>();

            List<Block> blocks = new ArrayList();
            for (int i = 0; i < n; i++) {
                Block b = new Block();
                blocks.add(b);
                b.r = layer_regions.get(i);
                layer_x_dominance.put(b.r.index, new ArrayList<>());
                layer_y_dominance.put(b.r.index, new ArrayList<>());
            }
            blocks.sort((a, b) -> {
                int r = Double.compare(
                        a.r.getOriginalLocation().getX() + a.r.getOriginalLocation().getY(),
                        b.r.getOriginalLocation().getX() + b.r.getOriginalLocation().getY());
                if (r == 0) {
                    return Double.compare(
                            a.r.getOriginalLocation().getX() - a.r.getOriginalLocation().getY(),
                            b.r.getOriginalLocation().getX() - b.r.getOriginalLocation().getY());
                } else {
                    return r;
                }
            });
            for (int i = 0; i < blocks.size(); i++) {
                blocks.get(i).rankXplusY = i;
            }
            blocks.sort((a, b) -> {
                int r = Double.compare(
                        a.r.getOriginalLocation().getX() - a.r.getOriginalLocation().getY(),
                        b.r.getOriginalLocation().getX() - b.r.getOriginalLocation().getY());
                if (r == 0) {
                    return Double.compare(
                            a.r.getOriginalLocation().getX() + a.r.getOriginalLocation().getY(),
                            b.r.getOriginalLocation().getX() + b.r.getOriginalLocation().getY());
                } else {
                    return r;
                }
            });
            for (int i = 0; i < blocks.size(); i++) {
                blocks.get(i).rankXminY = i;
            }

            // sorted on X-Y --> X+Y
            for (int i = 0; i < blocks.size(); i++) {
                Block a = blocks.get(i);

                Block prev = null;
                for (int j = i - 1; j >= 0; j--) {
                    Block b = blocks.get(j);
                    if (b.rankXplusY > a.rankXplusY) {
                        continue;
                    }
                    if (prev == null || b.rankXplusY > prev.rankXplusY) {
                        // include relation

                        // Since the rectangles of the regions are defined by their middle point, these are the offsets of the centerpoints
                        double delta_x = b.r.getOriginalLocation().getX() - a.r.getOriginalLocation().getX();
                        double delta_y = b.r.getOriginalLocation().getY() - a.r.getOriginalLocation().getY();

                        if(Math.abs(delta_x) >= Math.abs(delta_y)){
                            if(delta_x < 0) {
                                layer_x_dominance.get(a.r.index).add(b.r.index);
                            } else {
                                layer_x_dominance.get(b.r.index).add(a.r.index);
                            }
                        } else {
                            if(delta_y < 0) {
                                layer_y_dominance.get(a.r.index).add(b.r.index);
                            } else {
                                layer_y_dominance.get(b.r.index).add(a.r.index);
                            }
                        }

                        prev = b;
                    }
                }
            }
            for (int i = 0; i < blocks.size(); i++) {
                Block a = blocks.get(i);

                Block prev = null;
                for (int j = i + 1; j < blocks.size(); j++) {
                    Block b = blocks.get(j);
                    if (b.rankXplusY > a.rankXplusY) {
                        continue;
                    }
                    if (prev == null || b.rankXplusY > prev.rankXplusY) {
                        // include relation

                        // Since the rectangles of the regions are defined by their middle point, these are the offsets of the centerpoints
                        double delta_x = b.r.getOriginalLocation().getX() - a.r.getOriginalLocation().getX();
                        double delta_y = b.r.getOriginalLocation().getY() - a.r.getOriginalLocation().getY();


                        if(Math.abs(delta_x) >= Math.abs(delta_y)){
                            if(delta_x < 0) {
                                layer_x_dominance.get(a.r.index).add(b.r.index);
                            } else {
                                layer_x_dominance.get(b.r.index).add(a.r.index);
                            }
                        } else {
                            if(delta_y < 0) {
                                layer_y_dominance.get(a.r.index).add(b.r.index);
                            } else {
                                layer_y_dominance.get(b.r.index).add(a.r.index);
                            }
                        }

                        prev = b;
                    }
                }
            }

            x_dominance_relation.put(layer, layer_x_dominance);
            y_dominance_relation.put(layer, layer_y_dominance);
        }
    }

    private void temp(String ipe_path) throws IOException {

        if (ipe_path == null) return;

        HashMap<String, Polygon> input_polygons = new HashMap<>();
        HashMap<String, Double> area_percentages = new HashMap<>();
        double max_value = 0;

        IPEReader read = IPEReader.fileReader(new File(ipe_path));
        List<ReadItem> items = read.read();

        for (ReadItem label_item : items) {
            if (label_item.getGeometry().getGeometryType() == GeometryType.VECTOR) {
                Vector label_pos = (Vector) label_item.getGeometry();
                for (ReadItem outline_item : items) {
                    if (outline_item.getGeometry().getGeometryType() == GeometryType.POLYGON) {
                        Polygon pol = (Polygon) outline_item.getGeometry();
                        if (pol.contains(label_pos, DoubleUtil.EPS)) {
                            input_polygons.put(label_item.getString(), pol);
                            if (pol.areaUnsigned() > max_value) max_value = pol.areaUnsigned();
                        }
                    }
                }
            }
        }
        DecimalFormat df = new DecimalFormat("#.#####");

        for (Map.Entry<String, Polygon> entry : input_polygons.entrySet()) {
            System.out.println(entry.getKey() + "\t " + df.format(entry.getValue().areaUnsigned() / max_value) + "\t" + entry.getValue().centroid().getX() + "\t" + entry.getValue().centroid().getY());
            area_percentages.put(entry.getKey(), entry.getValue().areaUnsigned() / max_value);
        }
        read.close();

    }

    private void loadSecondSeparation(String bb_path) throws IOException {
        File file = new File(bb_path);
        BufferedReader br = new BufferedReader(new FileReader(file));
        String st;
        //System.out.println(stabilities.keySet());

        HashMap<String, Rectangle> input_bounding_boxes = new HashMap<>();

        br.readLine();
        while ((st = br.readLine()) != null){
            String[] tokens = st.split("\t");
            input_bounding_boxes.put(tokens[0], Rectangle.byCenterAndSize(Vector.subtract(new Vector(Double.parseDouble(tokens[1]), Double.parseDouble(tokens[2])), input_bounding_box.leftBottom()), Double.parseDouble(tokens[3]), Double.parseDouble(tokens[4])));
        }
        br.close();

        for (Region region : index_to_region.values()) {
            region.setInputBoundingBox(input_bounding_boxes.get(region.code));
        }

        x_second_separation = new HashMap<>();
        y_second_separation = new HashMap<>();
        for (Region region_1 : index_to_region.values()) {
            Rectangle bb_1 = input_bounding_boxes.get(region_1.code);
            for (Region region_2 : index_to_region.values()) {
                if(region_1.equals(region_2)) continue;
                //System.out.println(region_1.code + " : " + region_2.code);
                x_second_separation.computeIfAbsent(region_2.index, k -> new ArrayList<>());
                y_second_separation.computeIfAbsent(region_2.index, k -> new ArrayList<>());
                Rectangle bb_2 = input_bounding_boxes.get(region_2.code);

                if (bb_1.getRight() < bb_2.getLeft()) {
                    x_second_separation.get(region_2.index).add(region_1.index);
                }
                if (bb_1.getTop() < bb_2.getBottom()) {
                    y_second_separation.get(region_2.index).add(region_1.index);
                }
            }
        }
    }

    private void readSecondSeparationFromIPEFile(String ipe_path) throws IOException {

        if(ipe_path == null) return;

        //String bb_file = "Code\tX\tY\tWidth\tHeight\n";

        //HashMap<String, Polygon> input_polygons = new HashMap<>();
        //HashMap<String, Double> area_percentages = new HashMap<>();
        //double max_value = 0;

        /*
        input_bounding_boxes = new HashMap<>();
        IPEReader read = IPEReader.fileReader(new File(ipe_path));
        List<ReadItem> items = read.read();

        for (ReadItem label_item : items) {
            if (label_item.getGeometry().getGeometryType() == GeometryType.VECTOR) {
                Vector label_pos = (Vector) label_item.getGeometry();
                for (ReadItem outline_item : items) {
                    if (outline_item.getGeometry().getGeometryType() == GeometryType.POLYGON) {
                        Polygon pol = (Polygon) outline_item.getGeometry();
                        if(pol.contains(label_pos, DoubleUtil.EPS)){
                            Rectangle bb =  new Rectangle();
                            bb.includeGeometry(pol);
                            input_bounding_boxes.put(label_item.getString(), bb);
                            //input_polygons.put(label_item.getString(), pol);
                            //if(pol.areaUnsigned()>max_value) max_value = pol.areaUnsigned();

                            index_to_region.get(code_to_index.get(label_item.getString())).setInputBoundingBox(bb);
                            index_to_region.get(code_to_index.get(label_item.getString())).setInputPolygon(new GeometryGroup(pol));

                        }
                    }
                }
            }
        }
        */


        HashMap<String, Rectangle> input_bounding_boxes = new HashMap<>();

        /*
        for (int k = 4; k < 18; k++) {
            ipe_path = "data/ipe/gem_"+(2000+k)+".ipe";
            HashMap<Rectangle, GeometryGroup> groups = new HashMap<>();
            IPEReader read = IPEReader.fileReader(new File(ipe_path));
            List<ReadItem> items = read.read();

            // first, filter out the shapes and compute their bounding boxes
            List<Rectangle> bbs = new ArrayList();
            List<GeometryGroup<Polygon>> polies = new ArrayList();

            for (ReadItem poly_item : items) {
                if (poly_item.getGeometry().getGeometryType() == GeometryType.VECTOR) {
                    continue;
                }
                GeometryGroup<Polygon> geom;
                if (poly_item.getGeometry().getGeometryType() == GeometryType.POLYGON) {
                    geom = new GeometryGroup(poly_item.getGeometry());
                } else {
                    geom = (GeometryGroup) poly_item.getGeometry();
                }

                polies.add(geom);
                bbs.add(Rectangle.byBoundingBox(geom));

            }

            // now, treat the rest (the labels)

            for (ReadItem label_item : items) {
                if (label_item.getGeometry().getGeometryType() != GeometryType.VECTOR) {
                    continue;
                }
                if(!code_to_index.containsKey(label_item.getString())) continue;

                Vector label_pos = (Vector) label_item.getGeometry();
                // find the geometry such that the number of containing
                // polygons is odd (winding number...),
                // and such that the polygon is smallest
                int myIndex = -1;
                double myArea = Double.POSITIVE_INFINITY;
                for (int i = 0; i < bbs.size(); i++) {
                    GeometryGroup<Polygon> geom = polies.get(i);
                    Rectangle box = bbs.get(i);
                    // so: find smallest containing polygon, odd nesting number within the object

                    if (!box.contains(label_pos)) {
                        // not even close
                        continue;
                    }

                    int containment = 0;
                    double minarea = Double.POSITIVE_INFINITY;
                    for (Polygon p : geom.getParts()) {
                        if (p.contains(label_pos)) {
                            containment++;
                            double a = p.areaUnsigned();
                            minarea = Math.min(minarea, a);
                        }
                    }
                    if (containment % 2 == 0) {
                        // even winding number, so outside
                        continue;
                    }

                    if (0 < minarea && minarea < myArea) {
                        myIndex = i;
                        myArea = minarea;
                    }
                }

                if(!input_bounding_boxes.containsKey(label_item.getString())) {
                    input_bounding_boxes.put(label_item.getString(), bbs.get(myIndex));

                    index_to_region.get(code_to_index.get(label_item.getString())).setInputBoundingBox(bbs.get(myIndex));
                    index_to_region.get(code_to_index.get(label_item.getString())).setInputPolygon(polies.get(myIndex));

                    bb_file += label_item.getString() + "\t" + Math.round(bbs.get(myIndex).center().getX() * 10000)/10000.0 + "\t" + Math.round(bbs.get(myIndex).center().getY() * 10000)/10000.0 + "\t" + Math.round(bbs.get(myIndex).width() * 10000)/10000.0 + "\t" + Math.round(bbs.get(myIndex).height() * 10000)/10000.0 + "\n";
                }
                //System.out.println(label_item.getString() + "\t" + Math.round(bbs.get(myIndex).center().getX() * 10000)/10000.0 + "\t" + Math.round(bbs.get(myIndex).center().getY() * 10000)/10000.0 + "\t" + Math.round(bbs.get(myIndex).width() * 10000)/10000.0 + "\t" + Math.round(bbs.get(myIndex).height() * 10000)/10000.0);
            }
            System.out.println(ipe_path);
        }*/

        HashMap<Rectangle, GeometryGroup> groups = new HashMap<>();
        IPEReader read = IPEReader.fileReader(new File(ipe_path));
        List<ReadItem> items = read.read();

        // first, filter out the shapes and compute their bounding boxes
        List<Rectangle> bbs = new ArrayList();
        List<GeometryGroup<Polygon>> polies = new ArrayList();

        for (ReadItem poly_item : items) {
            if (poly_item.getGeometry().getGeometryType() == GeometryType.VECTOR) {
                continue;
            }
            GeometryGroup<Polygon> geom;
            if (poly_item.getGeometry().getGeometryType() == GeometryType.POLYGON) {
                geom = new GeometryGroup(poly_item.getGeometry());
            } else {
                geom = (GeometryGroup) poly_item.getGeometry();
            }

            polies.add(geom);
            bbs.add(Rectangle.byBoundingBox(geom));

        }

        // now, treat the rest (the labels)

        //String bb_file = "Code\tX\tY\tWidth\tHeight\n";
        for (ReadItem label_item : items) {
            if (label_item.getGeometry().getGeometryType() != GeometryType.VECTOR) {
                continue;
            }
            if(!code_to_index.containsKey(label_item.getString())) continue;

            Vector label_pos = (Vector) label_item.getGeometry();
            // find the geometry such that the number of containing
            // polygons is odd (winding number...),
            // and such that the polygon is smallest
            int myIndex = -1;
            double myArea = Double.POSITIVE_INFINITY;
            for (int i = 0; i < bbs.size(); i++) {
                GeometryGroup<Polygon> geom = polies.get(i);
                Rectangle box = bbs.get(i);
                // so: find smallest containing polygon, odd nesting number within the object

                if (!box.contains(label_pos)) {
                    // not even close
                    continue;
                }

                int containment = 0;
                double minarea = Double.POSITIVE_INFINITY;
                for (Polygon p : geom.getParts()) {
                    if (p.contains(label_pos)) {
                        containment++;
                        double a = p.areaUnsigned();
                        minarea = Math.min(minarea, a);
                    }
                }
                if (containment % 2 == 0) {
                    // even winding number, so outside
                    continue;
                }

                if (0 < minarea && minarea < myArea) {
                    myIndex = i;
                    myArea = minarea;
                }
            }

            input_bounding_boxes.put(label_item.getString(), bbs.get(myIndex));

            index_to_region.get(code_to_index.get(label_item.getString())).setInputBoundingBox(bbs.get(myIndex));
            index_to_region.get(code_to_index.get(label_item.getString())).setInputPolygon(polies.get(myIndex));

            //bb_file += label_item.getString() + "\t" + Math.round(bbs.get(myIndex).center().getX() * 10000)/10000.0 + "\t" + Math.round(bbs.get(myIndex).center().getY() * 10000)/10000.0 + "\t" + Math.round(bbs.get(myIndex).width() * 10000)/10000.0 + "\t" + Math.round(bbs.get(myIndex).height() * 10000)/10000.0 + "\n";
            //System.out.println(label_item.getString() + "\t" + Math.round(bbs.get(myIndex).center().getX() * 10000)/10000.0 + "\t" + Math.round(bbs.get(myIndex).center().getY() * 10000)/10000.0 + "\t" + Math.round(bbs.get(myIndex).width() * 10000)/10000.0 + "\t" + Math.round(bbs.get(myIndex).height() * 10000)/10000.0);
        }
        //System.out.println(ipe_path);

        /*
        try {
            String name = ipe_path.split("\\.")[0].split("/")[2];
            Files.write(Paths.get("data/bbs/NL.tsv"), bb_file.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        /*
        for (Map.Entry<String, Polygon> entry : input_polygons.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue().areaUnsigned()/max_value);
            area_percentages.put(entry.getKey(), entry.getValue().areaUnsigned()/max_value);
        }*/

        x_second_separation = new HashMap<>();
        y_second_separation = new HashMap<>();
        for (Region region_1 : index_to_region.values()) {
            Rectangle bb_1 = input_bounding_boxes.get(region_1.code);
            for (Region region_2 : index_to_region.values()) {
                if(region_1.equals(region_2)) continue;
                //System.out.println(region_1.code + " : " + region_2.code);
                x_second_separation.computeIfAbsent(region_2.index, k -> new ArrayList<>());
                y_second_separation.computeIfAbsent(region_2.index, k -> new ArrayList<>());
                Rectangle bb_2 = input_bounding_boxes.get(region_2.code);

                if (bb_1.getRight() < bb_2.getLeft()) {
                    x_second_separation.get(region_2.index).add(region_1.index);
                }
                if (bb_1.getTop() < bb_2.getBottom()) {
                    y_second_separation.get(region_2.index).add(region_1.index);
                }
            }
        }

        read.close();

        /*
        System.out.println("X second");
        x_second_separation.forEach((integer, integers) -> System.out.println(index_to_region.get(integer).code + ": " + integers.stream().map(integer1 -> index_to_region.get(integer1).code).collect(Collectors.toList()).toString()));
        System.out.println("\nY second");
        y_second_separation.forEach((integer, integers) -> System.out.println(index_to_region.get(integer).code + ": " + integers.stream().map(integer1 -> index_to_region.get(integer1).code).collect(Collectors.toList()).toString()));
        */
    }


    /*
    private void createSeparationNeighbours() {
        x_dominance_relation = new HashMap<>();

        for (int layer = 0; layer < layer_count; layer++) {
            List<Region> layer_regions = regions.get(layer);
            layer_regions.sort(Comparator.comparingDouble(r -> r.getOriginalLocation().getX()));


            HashMap<Integer, List<Integer>> layer_x_dominance = new HashMap<>();
            HashMap<Integer, List<Integer>> layer_y_dominance = new HashMap<>();

            for (int i = 0; i < layer_regions.size(); i++) {
                Region r_i = layer_regions.get(i);
                List<Integer> x_dominance_i = new ArrayList<>();
                List<Integer> y_dominance_i = new ArrayList<>();
                for (int j = 0; j < layer_regions.size(); j++) {
                    if(i==j) continue;
                    Region r_j = layer_regions.get(j);

                    double delta_x = Math.abs(r_j.getOriginalLocation().getX() - r_i.getOriginalLocation().getX());
                    double delta_y = Math.abs(r_j.getOriginalLocation().getY() - r_i.getOriginalLocation().getY());

                    if(delta_x >= delta_y){
                        if(r_j.getOriginalLocation().getX() <= r_i.getOriginalLocation().getX())
                            x_dominance_i.add(r_j.index);
                    } else {
                        if(r_j.getOriginalLocation().getY() <= r_i.getOriginalLocation().getY())
                            y_dominance_i.add(r_j.index);
                    }

                }
                //x_min = r_i.getOriginalLocation().getX();
                //y_min = r_i.getOriginalLocation().getY();
                layer_x_dominance.put(r_i.index, x_dominance_i);
                layer_y_dominance.put(r_i.index, y_dominance_i);
            }

            x_dominance_relation.put(layer, layer_x_dominance);
            y_dominance_relation.put(layer, layer_y_dominance);
        }

        x_dominance_relation = new HashMap<>();
        for (int layer = 0; layer < layer_count; layer++) {
            List<Region> layer_regions = regions.get(layer);
            layer_regions.sort(Comparator.comparingDouble(r -> r.getOriginalLocation().getX()));

            HashMap<Integer, List<Integer>> layer_x_dominance = new HashMap<>();

            for (int i = 0; i < layer_regions.size(); i++) {
                Region r_i = layer_regions.get(i);
                List<Integer> x_dominance_i = new ArrayList<>();
                for (int j = 0; j < layer_regions.size(); j++) {
                    if(i==j) continue;
                    Region r_j = layer_regions.get(j);

                    if(r_j.getOriginalLocation().getX() <= r_i.getOriginalLocation().getX())
                        x_dominance_i.add(r_j.index);

                }
                layer_x_dominance.put(r_i.index, x_dominance_i);
            }
            x_dominance_relation.put(layer, layer_x_dominance);
        }

        y_dominance_relation = new HashMap<>();
        for (int layer = 0; layer < layer_count; layer++) {
            List<Region> layer_regions = regions.get(layer);
            layer_regions.sort(Comparator.comparingDouble(r -> r.getOriginalLocation().getX()));

            HashMap<Integer, List<Integer>> layer_y_dominance = new HashMap<>();

            for (int i = 0; i < layer_regions.size(); i++) {
                Region r_i = layer_regions.get(i);
                List<Integer> y_dominance_i = new ArrayList<>();
                for (int j = 0; j < layer_regions.size(); j++) {
                    if(i==j) continue;
                    Region r_j = layer_regions.get(j);

                    if(r_j.getOriginalLocation().getY() <= r_i.getOriginalLocation().getY())
                        y_dominance_i.add(r_j.index);

                }
                layer_y_dominance.put(r_i.index, y_dominance_i);
            }
            y_dominance_relation.put(layer, layer_y_dominance);
        }
    }
    */
}
