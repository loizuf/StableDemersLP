package Main;

import nl.tue.geometrycore.geometry.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RunResults {

    // new positions of the regions, key = region.id, value = x, y value
    private HashMap<Integer, HashMap<Integer, Vector>> positions;

    // new sizes of obstacles, key = region.id, value = size
    private HashMap<Integer, HashMap<Integer, Double>> sizes;
    private Map<Integer, Integer> lost_adjacencies;

    private List<Double> run_times;
    private List<Integer> run_iterations;
    private List<Boolean> run_successes;

    public RunResults() {
        run_times = new ArrayList<>();
        run_iterations = new ArrayList<>();
        run_successes = new ArrayList<>();
        lost_adjacencies = new HashMap<>();
        sizes = new HashMap<>();
        positions = new HashMap<>();
    }

    public void addPositions(HashMap<Integer, HashMap<Integer, Vector>> positions) {
        for (Integer layer : positions.keySet()) {
            HashMap<Integer, Vector> layer_positions = new HashMap<>();
            layer_positions.putAll(positions.get(layer));
            this.positions.put(layer, layer_positions);
        }
    }

    public void addSizes(HashMap<Integer, HashMap<Integer, Double>> sizes) {
        for (Integer layer : sizes.keySet()) {
            HashMap<Integer, Double> layer_sizes = new HashMap<>();
            layer_sizes.putAll(sizes.get(layer));
            this.sizes.put(layer, layer_sizes);
        }
    }

    public void addRuntime(double runtime) {
        run_times.add(runtime);
    }

    public void addIterations(int iteration_count) { run_iterations.add(iteration_count); }

    public void addSuccess(boolean success) {
        run_successes.add(success);
    }

    public List<Double> getRuntimes(){
        return run_times;
    }

    public List<Integer> getIterations() {
        if (run_iterations.size() > 0) return run_iterations;
        else return null;
    }

    public boolean wasSuccessful() {
        for (Boolean run_success : run_successes) {
            if(!run_success) return false;
        }
        return true;
    }

    public HashMap<Integer, HashMap<Integer, Vector>> getPositions() {
        return positions;
    }

    public HashMap<Integer, HashMap<Integer, Double>> getSizes() {
        return sizes;
    }

    public void addLostAdjacencies(Map<Integer, Integer> lost_adjacencies) {
        this.lost_adjacencies.putAll(lost_adjacencies);
    }

    public Map<Integer, Integer> getLostAdjacencies() {
        return lost_adjacencies;
    }
}
