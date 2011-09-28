/*
* This file is part of the GAIA-Tools software.
* Copyright 2011 University of Maryland
* 
* GAIA-Tools is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
* 
* GAIA-Tools is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with GAIA-Tools.  If not, see <http://www.gnu.org/licenses/>.
* 
*/
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