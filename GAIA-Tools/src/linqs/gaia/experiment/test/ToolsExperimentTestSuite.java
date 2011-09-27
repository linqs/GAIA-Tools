package linqs.gaia.experiment.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class ToolsExperimentTestSuite {

	public static Test suite() {
		TestSuite suite = new TestSuite();

		// Test each experiment class
		suite.addTestSuite(GraphGeneratorExperimentTestCase.class);
		
		return suite;
	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(suite());
	}
}