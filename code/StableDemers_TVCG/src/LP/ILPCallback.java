package LP;

import Main.RunResults;
import Main.Settings;
import Model.CartogramModel;
import ilog.concert.IloException;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplexModeler;
import nl.tue.geometrycore.geometry.Vector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class ILPCallback extends IloCplex.IncumbentCallback {

    private final CartogramCplex program;
    private CplexSolver solver;
    private CartogramModel c;
    int counter;

    public ILPCallback(CartogramCplex program, CplexSolver cplexSolver, CartogramModel c) {
        this.program = program;
        this.solver = cplexSolver;
        this.c = c;
        counter = 0;
    }

    @Override
    protected void main() throws IloException {
//        System.out.println(getIncumbentObjValue());

        RunResults results = new RunResults();

        results.addRuntime(program.getCplexTime() - solver.getStart());
        results.addIterations(program.getNiterations());
        results.addSuccess(program.isSolved());

        results.addLostAdjacencies(calculate_lost_adjacencies(program));
        results.addPositions(retrieve_calculated_region_positions(program));
        results.addSizes(retrieve_calculated_obstacle_sizes(program));

        for (int layer = 0; layer < c.getLayerCount(); layer++) {
            c.updateRegionPositions(layer, results.getPositions().get(layer));
            if(Settings.solution_method.equals("LP")){
                c.updateObstacleSizes(layer, results.getSizes().get(layer));
            }
        }

        if(Settings.write_eval_file){
            //cartogram.evaluateToFile(Settings.out_path + instance_name + "_eval.json", result);
            try {
                c.evaluateToFile(Settings.out_path + Settings.instance_name + "_eval.json", results, false);
                if(Settings.round_output) {
                    c.evaluateToFile(Settings.out_path + Settings.instance_name + "_eval_r.json", results, true);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Evaluated");
        }

        if(Settings.write_region_report) {
            //cartogram.writeRegionReportToFile(Settings.out_path + instance_name + "_regions.json", buildInstanceName());
            try {
                c.writeRegionReportToFile(Settings.out_path + Settings.instance_name + "_regions.json");
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Regions written to File");
        }

        if(Settings.GUI || Settings.draw_to_file) {
            System.out.println("Drawing");
            try {
                Main.Main.draw(c, Settings.instance_name);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private HashMap<Integer, HashMap<Integer, Vector>> retrieve_calculated_region_positions(CartogramCplex program) throws IloException {
        if(!program.isSolved()) System.out.println("This program is not yet solved. Retrieving new positions is not meaningful.");
        HashMap<Integer, HashMap<Integer, Vector>> positions = new HashMap<>();
        for (Integer layer : program.getLayers()) {
            System.out.println("layer: " + layer);
            positions.put(layer, this.getRegionPositions(layer));
        }
        return positions;
    }

    private HashMap<Integer, Vector> getRegionPositions(Integer layer) throws IloException {
        HashMap<Integer,Vector> positions = new HashMap<>();
        HashMap<Integer, IloNumVar> xs = program.get$x_coord().get(layer);
        HashMap<Integer, IloNumVar> ys = program.get$y_coord().get(layer);
        for (Integer id : xs.keySet()){
            try{
                positions.put(id, new Vector(this.getValue(xs.get(id)), this.getValue(ys.get(id))));
            } catch (IloCplexModeler.Exception e) {
                System.out.println(e + ":" + xs.get(id) + " was not found in the program.");
                System.out.println(ys.get(id) + " was not found in the program.");
            }
        }
        return positions;
    }

    private Map<Integer, Integer> calculate_lost_adjacencies(CartogramCplex program) throws IloException {
        if(!program.isSolved()) System.out.println("This program is not yet solved. Calculating lostadjacencies is not meaningful.");
        Map<Integer, Integer> lost_adjacencies = new HashMap<>();
        if(Settings.LP.distance_minimization_c){
            for (Integer layer : program.getLayers()) {
                int counter = 0;
                ArrayList<IloNumVar[]> vars = program.getAdjacencyMinimizers(layer);
                for (IloNumVar[] var_pair : vars) {
                    if(!Settings.LP.L_infinity) {
                        if(this.getValue(var_pair[0]) > 0.0 || this.getValue(var_pair[1]) > 0.0) counter++;
                    } else {
                        if(this.getValue(var_pair[0]) > 0.0) counter++;
                    }
                }
                lost_adjacencies.put(layer, counter);
            }
        }
        if(Settings.LP.topology_c) {
            for (Integer layer : program.getLayers()) {
                int counter = 0;
                ArrayList<IloNumVar> vars = program.getAdjacencyCounters(layer);
                for (IloNumVar var : vars) {
                    counter += Math.round(this.getValue(var));
                }
                lost_adjacencies.put(layer, counter);
            }
        }

        return lost_adjacencies;
    }

    private HashMap<Integer, HashMap<Integer, Double>> retrieve_calculated_obstacle_sizes(CartogramCplex program) throws IloException {
        if(!program.isSolved()) System.out.println("This program is not yet solved. Retrieving new sizes is not meaningful.");
        HashMap<Integer, HashMap<Integer, Double>> positions = new HashMap<>();
        for (Integer layer : program.getLayers()) {
            positions.put(layer, program.getObstacleSizes(layer));
        }
        return positions;
    }

    private HashMap<Integer, Double> getObstacleSizes(Integer layer) throws IloException {
        HashMap<Integer, Double> sizes = new HashMap<>();
        HashMap<Integer, IloNumVar> widths = program.get$widths().get(layer);
        for (Integer id : widths.keySet()){
            try{
                sizes.put(id, getValue(widths.get(id)));
            } catch (IloCplexModeler.Exception e) {
                System.out.println(widths.get(id) + " was not found in the program.");
            }
        }
        return sizes;
    }
}
