package Main;

import nl.tue.geometrycore.geometry.Vector;
import nl.tue.geometrycore.util.DoubleUtil;

import java.awt.*;
import java.io.File;

public class Settings {


    // Filepaths
    public static String out_path = "outputs/";
    public static String weight_path = "data/weights/";
    public static String topology_path = "data/topo/";
    public static String location_path = "data/locs/";
    public static String stability_path = "data/stability/";
    public static String normalization_path = "data/norm/";
    public static String instance_name = "";
    public static String ipe_path = null;
    public static String bb_path = null;

    public static String solution_method = "LP";
    public static String export_extension = ".lp";
    //public static boolean collective_normalization_flag = true;

    public static boolean GUI = true;
    public static boolean write_region_report = true;
    public static boolean write_eval_file = true;
    public static boolean draw_to_file;
    public static boolean colour_by_regions;
    public static Color[] colour_palette = {
            new Color(160,236,218),
            new Color(222,177,224),
            new Color(164,201,146),
            new Color(170,187,234),
            new Color(212,226,171),
            new Color(124,204,238),
            new Color(227,194,151),
            new Color(106,209,218),
            new Color(236,170,174),
            new Color(145,200,177)
    };
    public static boolean round_output = false;

    public static void set_solution_method(String solution_method) {
        if(!solution_method.equals("LP") && !solution_method.equals("Force")) System.out.println(String.format("The chosen solver %s is not a valid option. Please choose from 'LP' and 'Force'.", solution_method));
        else {
            Settings.solution_method = solution_method;
        }
    }

    //public static void set_collective_normalization(boolean col_norm_flag) { Settings.collective_normalization_flag = col_norm_flag; }

    public static class LP {
        // cplex Settings
        public static int THREADS = 1;

        // Constraint switches
        public static boolean disjoint_ilp_c = false;
        public static boolean disjoint_c = true;
        public static boolean double_disjont_c =  false;
        public static boolean distance_minimization_c = true;
        public static boolean topology_c = false;
        public static boolean angle_nuance_c = true;
        public static boolean origin_displacement_c = false;
        public static boolean stability_c = false;

        // Objective function weights
        public static double factor_dist_min = 1;
        public static double factor_topology = 1;
        public static double factor_angle_nuance = 0.001;
        public static double factor_orig_displ = 1;
        public static double factor_stability = 0.1;

        // Stability relations, Iterative solving settings and Dominance
        // We create a model for all layers together if they are in the same set in iteration_sets
        //TODO: public static boolean use_disjoint_dominance = false;
        public static boolean L_infinity = false;

        // Constants
        public static double min_contact_factor = 0.25;
        //public static double EPS = DoubleUtil.EPS;
        public static double nuance_non_adjacent_multiplier = 0.1;
        public static double obstacle_lower_bound = 0.5;
        public static double obstacle_upper_bound = 1.5;
        //public static double big_constant = Double.MAX_VALUE; // This must be set in relation to the LP
        public static boolean mip_warmstart = false;
        public static boolean export_LP_model = false;
        public static double big_constant = 20000;
        public static double time_limit = 36000;
    }

    public static class Force {
        public static int max_iterations = 50000;

        public static boolean disjoint_f = true;
        public static boolean origin_f = false;
        public static boolean topological_f = true;
        public static boolean stability_f = false;

        public static double disjoint_weight = 40;
        public static double origin_weight = 1;
        public static double topological_weight = 1;
        public static double stability_weight = 1;

        public static double minimal_movement = 0.0001;
        public static String speed_up = "grid";
        public static double cooling_factor = 1;
    }
}
