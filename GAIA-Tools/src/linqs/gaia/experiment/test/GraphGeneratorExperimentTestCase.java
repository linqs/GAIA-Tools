package linqs.gaia.experiment.test;


import junit.framework.TestCase;
import linqs.gaia.experiment.Experiment;
import linqs.gaia.experiment.GraphGeneratorExperiment;

public class GraphGeneratorExperimentTestCase extends TestCase {
	public GraphGeneratorExperimentTestCase() {
		
	}
	
	protected void setUp() {
		
	}

	protected void tearDown() {
		
	}
	
	public void testExperiment() {
		Experiment e = new GraphGeneratorExperiment();
		e.loadParametersFile("resource/SampleFiles/GraphGeneratorExperimentSample/experiment.cfg");
		e.runExperiment();
		
		assertNotNull(e);
	}
	
	public static void main(String[] args) {
		GraphGeneratorExperimentTestCase gg = new GraphGeneratorExperimentTestCase();
		gg.testExperiment();
	}
}
