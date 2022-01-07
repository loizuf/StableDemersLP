package Model;

import Main.Settings;
import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.geometry.linear.Rectangle;
import nl.tue.geometrycore.util.DoubleUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Evaluation {

    static HashMap<String, Double> stability_totals;
    static List<Double> stability_input_totals;
    static List<Double> origin_displacement_totals;
    static HashMap<String, Double> inter_layer_displacement_totals;

    public static HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, Double>>>> calculateStabilityInterLayer(CartogramModel c){

        //stability_totals = new HashMap<>();
        HashMap<Integer, HashMap<Integer, HashMap<String, HashMap<String, Double>>>> stabilities = new HashMap<>();

        for (int layer_a = 0; layer_a < c.getLayerCount(); layer_a++) {
            HashMap<Integer, HashMap<String, HashMap<String, Double>>> first_layer_map = new HashMap<>();
            for (int layer_b = layer_a; layer_b < c.getLayerCount(); layer_b++) {
                if(layer_a == layer_b) continue;
                HashMap<String, HashMap<String, Double>> second_layer_map = new HashMap<>();
                for (Region region_1 : c.getRegions(layer_a)) {
                    HashMap<String, Double> region_map = new HashMap<>();
                    for (Region region_2 : c.getRegions(layer_b)) {
                        if(region_1.index == region_2.index) continue;
                        Double[] oldPercentages = getQuadrantPercentages(region_1.getRectangle(layer_a), region_2.getRectangle(layer_a));
                        Double[] newPercentages = getQuadrantPercentages(region_1.getRectangle(layer_b), region_2.getRectangle(layer_b));
                        double itemStability = getQuadrantStability(oldPercentages, newPercentages);

                        region_map.put(region_2.code, itemStability);
                    }
                    second_layer_map.put(region_1.code, region_map);
                    //stability_totals.put(layer_a + "-" + layer_b, )
                }
                first_layer_map.put(layer_b, second_layer_map);
            }
            stabilities.put(layer_a, first_layer_map);
        }
        return stabilities;
    }

    public static HashMap<Integer, HashMap<String, HashMap<String, Double>>> calculateStabilitytoInput(CartogramModel c){

        HashMap<Integer, HashMap<String, HashMap<String, Double>>> stabilities = new HashMap<>();

        for (int layer_a = 0; layer_a < c.getLayerCount(); layer_a++) {
            HashMap<String, HashMap<String, Double>> layer_map = new HashMap<>();
            for (Region region_1 : c.getRegions(layer_a)) {
                HashMap<String, Double> region_map = new HashMap<>();
                for (Region region_2 : c.getRegions(layer_a)) {
                    if(region_1.index == region_2.index) continue;
                    Double[] oldPercentages = getQuadrantPercentagesOverlapping(region_1.getInputBoundingBox(), region_2.getInputBoundingBox());
                    Double[] newPercentages = getQuadrantPercentagesOverlapping(region_1.getRectangle(layer_a), region_2.getRectangle(layer_a));
                    double itemStability = getQuadrantStability(oldPercentages, newPercentages);

                    region_map.put(region_2.code, itemStability);
                }
                layer_map.put(region_1.code, region_map);
            }

            stabilities.put(layer_a, layer_map);
        }
        return stabilities;
    }

    public static HashMap<Integer, HashMap<String, Double>> computeOriginDisplacement(CartogramModel c){
        HashMap<Integer, HashMap<String, Double>> origin_displacements = new HashMap<>();

        for (int layer = 0; layer < c.getLayerCount(); layer++) {
            HashMap<String, Double> layer_displacements = new HashMap<>();
            for (Region region : c.getRegions(layer)) {
                layer_displacements.put(region.code, Vector.subtract(region.getOriginalLocation(),region.getPosition(layer)).length());
            }
            origin_displacements.put(layer, layer_displacements);
        }
        return origin_displacements;
    }

    public static HashMap<Integer, HashMap<Integer, HashMap<String, Double>>> computeInterLayerDisplacement(CartogramModel c){
        HashMap<Integer, HashMap<Integer, HashMap<String, Double>>> layer_movements = new HashMap<>();

        for (int layer_a = 0; layer_a < c.getLayerCount(); layer_a++) {
            HashMap<Integer, HashMap<String, Double>> layer_displacements = new HashMap<>();
            for (int layer_b = 0; layer_b < c.getLayerCount(); layer_b++) {
                if(layer_a == layer_b) continue;
                HashMap<String, Double> inter_layer_displacements = new HashMap<>();
                for (Region region : c.getRegions(layer_a)) {
                    if(!c.isPresent(region, layer_b)) continue;
                    inter_layer_displacements.put(region.code, Vector.subtract(region.getPosition(layer_a), region.getPosition(layer_b)).length());
                }
                layer_displacements.put(layer_b, inter_layer_displacements);
            }
            layer_movements.put(layer_a, layer_displacements);
        }
        return layer_movements;
    }

    public static HashMap<Integer, Double> calculateDistanceForAdjacency(CartogramModel c) {
        HashMap<Integer, Double> adjacency_distances = new HashMap<>();
        for (int layer = 0; layer < c.getLayerCount(); layer++) {
            double layer_adjacency_distance = 0;
            int layer_adjacent_counter = 0;
            for (Region region_a : c.getRegions(layer)) {
                for (Region region_b : c.getNeighbours(region_a, layer)) {
                    if(c.adjacent(region_a, region_b, layer)) {
                        layer_adjacent_counter++;
                        double x_dist = Math.abs(region_a.getPosition(layer).getX() - region_b.getPosition(layer).getX());
                        double y_dist = Math.abs(region_a.getPosition(layer).getY() - region_b.getPosition(layer).getY());
                        double min_dist = (region_a.getSize(layer) + region_b.getSize(layer))/2.0;
                        //System.out.println("diances between "+ region_a.code + " and " + region_b.code + ": " + x_dist + " __ " + y_dist + " for a minimal distance of " + min_dist);
                        layer_adjacency_distance += Math.max(x_dist - min_dist, 0) + Math.max(y_dist - min_dist, 0);
                    }
                }
            }
            adjacency_distances.put(layer, layer_adjacency_distance/(2*layer_adjacent_counter));
        }
        return adjacency_distances;
    }

    public static HashMap<Integer, Integer> calculateLostAdjacencies(CartogramModel c) {
        HashMap<Integer, Integer> lost_adjacencies = new HashMap<>();
        for (int layer = 0; layer < c.getLayerCount(); layer++) {
            int lost_layer_adjacencies = 0;

            List<Region> regions = c.getRegions(layer);
            for (int i = 0; i < regions.size(); i++) {
                Region region_a = regions.get(i);
                for (int j = i+1; j < regions.size(); j++) {
                    Region region_b = regions.get(j);
                    if(c.adjacent(region_a, region_b, layer)) {
                        if (!adjKept(region_a, region_b, layer)) lost_layer_adjacencies++;
                    }
                }
            }

//            for (Region region_a : c.getRegions(layer)) {
//                for (Region region_b : c.getNeighbours(region_a, layer)) {
//                    if(!adjKept(region_a, region_b, layer)) lost_layer_adjacencies++;
//                }
//            }
            System.out.println("calculated in layer " + layer + ": " + lost_layer_adjacencies);
            lost_adjacencies.put(layer, lost_layer_adjacencies);
        }
        return lost_adjacencies;
    }

    public static boolean adjKept(Region r, Region s, int layer) {
        double x_dist = Math.abs(r.getPosition(layer).getX() - s.getPosition(layer).getX());
        double y_dist = Math.abs(r.getPosition(layer).getY() - s.getPosition(layer).getY());
        double dist_is_max = Math.max(x_dist, y_dist);
        double dist_is_min = Math.min(x_dist, y_dist);
        double min_contact_length = Math.min(r.getSize(layer), s.getSize(layer)) * Settings.LP.min_contact_factor;
        double dist_should = (r.getSize(layer) + s.getSize(layer))/2.0;
//        System.out.println(r.code + "-" + s.code + ": " + (dist_is - dist_should));
//        if (dist_is <= dist_should + 1){
        if (dist_is_max <= dist_should + DoubleUtil.EPS && dist_is_min <=dist_should + DoubleUtil.EPS - min_contact_length){
            return true;
        } else {
//            System.out.println(layer + " LOST: " + r.code + "-" + s.code);
            return false;
        }
    }

    /**
     * Returns an array of size 8 containing the percentage percentage of r2
     * that lies in the respective quadrant. Quadrants are encoded as follows:
     * 0:east,1norhteast,2north,3northwest,4west,5southwest,6south,7southeast
     *
     * @param r1
     * @param r2
     * @return
     */
    private static Double[] getQuadrantPercentages(Rectangle r1, Rectangle r2) {

        //check in which quardrants it lies
        double E = 0, NE = 0, N = 0, NW = 0, W = 0, SW = 0, S = 0, SE = 0;
        if (r2.getLeft() >= r1.getRight()) {
            //Strictly east
            if (r2.getTop() > r1.getTop()) {
                //at least partially in NE

                //get the percentage that r2 is in NE
                NE = (r2.getTop() - r1.getTop()) / r2.height();
                NE = Math.min(1, NE);
            }
            if (r1.getBottom() > r2.getBottom()) {
                //at least partiall in SE
                SE = (r1.getBottom() - r2.getBottom()) / r2.height();
                SE = Math.min(1, SE);
            }
            //remainder is in east
            E = 1 - NE - SE;

        } else if (r2.getRight() <= r1.getLeft()) {
            //strictly west
            if (r2.getTop() > r1.getTop()) {
                //at least partially in NW

                //get the percentage that r2 is in NW
                NW = (r2.getTop() - r1.getTop()) / r2.height();
                NW = Math.min(1, NW);
            }
            if (r1.getBottom() > r2.getBottom()) {
                //at least partiall in SW
                SW = (r1.getBottom() - r2.getBottom()) / r2.height();
                SW = Math.min(1, SW);
            }
            //remainder is in west
            W = 1 - NW - SW;
        } else if (r2.getBottom() >= r1.getTop()) {
            //strictly North
            if (r2.getLeft() < r1.getLeft()) {
                //at least partially in NW

                //get the percentage that r2 is in NW
                NW = (r1.getLeft() - r2.getLeft()) / r2.width();
                NW = Math.min(1, NW);
            }
            if (r2.getRight() > r1.getRight()) {
                //at least partiall in SW
                NE = (r2.getRight() - r1.getRight()) / r2.width();
                NE = Math.min(1, NE);
            }
            //remainder is in north
            N = 1 - NW - NE;
        } else {
            //strictly south
            if (r2.getLeft() < r1.getLeft()) {
                //at least partially in SW

                //get the percentage that r2 is in NW
                SW = (r1.getLeft() - r2.getLeft()) / r2.width();
                SW = Math.min(1, SW);
            }
            if (r2.getRight() > r1.getRight()) {
                //at least partiall in SE
                SE = (r2.getRight() - r1.getRight()) / r2.width();
                SE = Math.min(1, SE);
            }
            //remainder is in south
            S = 1 - SW - SE;
        }

        Double[] quadrant = new Double[8];
        quadrant[0] = E;
        quadrant[1] = NE;
        quadrant[2] = N;
        quadrant[3] = NW;
        quadrant[4] = W;
        quadrant[5] = SW;
        quadrant[6] = S;
        quadrant[7] = SE;
        return quadrant;
    }

    /**
     * Returns an array of size 8 containing the percentage percentage of r2
     * that lies in the respective quadrant. Quadrants are encoded as follows:
     * 0:east,1norhteast,2north,3northwest,4west,5southwest,6south,7southeast
     *
     * @param r1
     * @param r2
     * @return
     */
    private static Double[] getQuadrantPercentagesOverlapping(Rectangle r1, Rectangle r2) {

        //check in which quardrants it lies
        double E, NE, N, NW, W, SW, S, SE, C;
        double ePerc = 0, wPerc = 0, sPerc = 0, nPerc = 0;

        if (r2.getRight() > r1.getRight()) {
            //some east
            ePerc = (r2.getRight() - r1.getRight()) / r2.width();
            ePerc = Math.min(1, ePerc);
        }

        if (r2.getLeft() < r1.getLeft()) {
            //some west
            wPerc = (r1.getLeft() - r2.getLeft()) / r2.width();
            wPerc = Math.min(1, wPerc);
        }

        if (r2.getTop() > r1.getTop()) {
            //some north
            nPerc = (r2.getTop() - r1.getTop()) / r2.height();
            nPerc = Math.min(1, nPerc);
        }

        if (r2.getBottom() < r1.getBottom()) {
            //some south
            sPerc = (r1.getBottom() - r2.getBottom()) / r2.height();
            sPerc = Math.min(1, sPerc);
        }

        NE = nPerc * ePerc;
        SE = sPerc * ePerc;
        NW = nPerc * wPerc;
        SW = sPerc * wPerc;

        N = nPerc - NE - NW;
        S = sPerc - SE - SW;
        E = ePerc - NE - SE;
        W = wPerc - NW - SW;

        C = 1 - S - E - W - N - SE - NE - SW - NW;
        //System.out.println(C);

        Double[] quadrant = new Double[8];
        quadrant[0] = E;
        quadrant[1] = NE;
        quadrant[2] = N;
        quadrant[3] = NW;
        quadrant[4] = W;
        quadrant[5] = SW;
        quadrant[6] = S;
        quadrant[7] = SE;
        return quadrant;
    }

    /**
     * returns the stability of the movement between quadrants
     *
     * @param oldPercentages
     * @param newPercentages
     * @return
     */
    private static double getQuadrantStability(Double[] oldPercentages, Double[] newPercentages) {
        double stability = 0;
        for (int i = 0; i < oldPercentages.length; i++) {
            double oldPercentage = oldPercentages[i];
            double newPercentage = newPercentages[i];

            stability += Math.abs(oldPercentage - newPercentage) / 2;
        }
        return stability;
    }
}
