/*******************************************************************************
 * Copyright (c) 2014 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Initial API and implementation and/or initial documentation - Jay Jay Billings,
 *   Jordan H. Deyton, Dasha Gorin, Alexander J. McCaskey, Taylor Patterson,
 *   Claire Saunders, Matthew Wang, Anna Wojtowicz
 *******************************************************************************/
package org.eclipse.ice.item.nuclear;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.bind.annotation.XmlRootElement;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ice.datastructures.form.DataComponent;
import org.eclipse.ice.datastructures.form.Entry;
import org.eclipse.ice.datastructures.form.Form;
import org.eclipse.ice.datastructures.form.FormStatus;
import org.eclipse.ice.datastructures.updateableComposite.Component;
import org.eclipse.ice.item.jobLauncher.SuiteLauncher;

/**
 * A SuiteLauncher Item for all MOOSE products (MARMOT, BISON, RELAP-7, RAVEN).
 * The MOOSE framework is developed by Idaho National Lab.
 * 
 * @author w5q
 * 
 */

@XmlRootElement(name = "MOOSELauncher")
public class MOOSELauncher extends SuiteLauncher {

	/**
	 * The currently selected MOOSE application. Set by reviewEntries().
	 */
	private String execName = "";
	
	/**
	 * The name of the YAML/action syntax generator
	 */
	private static final String yamlSyntaxGenerator = 
			"Generate YAML/action syntax";
	
	/**
	 * Nullary constructor.
	 */
	public MOOSELauncher() {
		this(null);
	}

	/**
	 * Parameterized constructor.
	 */
	public MOOSELauncher(IProject projectSpace) {
		super(projectSpace);
	}

	/**
	 * Overriding setupForm to set the executable names and host information.
	 */
	@Override
	protected void setupForm() {

		// Local Declarations
		String localInstallDir = "/home/moose";
		String remoteInstallDir = "/home/bkj/moose";
//		String remoteInstallDir = "/home/moose";	// For testing

		// Create the list of executables
		ArrayList<String> executables = new ArrayList<String>();
		executables.add("MARMOT");
		executables.add("BISON");
		executables.add("RELAP-7");
		executables.add("RAVEN");
		executables.add("MOOSE_TEST");
		executables.add(yamlSyntaxGenerator);

		// Add the list to the suite
		addExecutables(executables);

		// Setup the Form
		super.setupForm();
		
		// Grab the DataComponent responsible for managing Input Files
		DataComponent inputFilesComp = (DataComponent) form
				.getComponent(1);
		// Set the input file to only *.i files (to reduce workspace clutter)
		inputFilesComp.deleteEntry("Input File");
		addInputType("Input File", "inputFile", 
				"The MOOSE input file that defines the problem.", ".i");
		
		// Add hosts
		addHost("localhost", "linux", localInstallDir);
		addHost("habilis.ornl.gov", "linux", remoteInstallDir);
//		addHost("megafluffy.ornl.gov", "linux", remoteInstallDir);	// For testing

		// Enable MPI
		enableMPI(1, 10000, 1);

		// Enable TBB
		enableTBB(1, 256, 1);

		return;
	}

	/**
	 * Overrides the base class operation to properly account for MOOSE's file
	 * structure.
	 * 
	 * @param installDir
	 *            The installation directory of MOOSE.
	 * @param executable
	 *            The name of the executable selected by a client.
	 * @return The complete launch command specific for a given MOOSE product,
	 *         determined by the executable name selected by the client.
	 */
	@Override
	protected String updateExecutablePath(String installDir, String executable) {

		// A HashMap of MOOSE product executables that can be launched
		HashMap<String, String> executableMap = new HashMap<String, String>();
		executableMap.put("MARMOT", "marmot");
		executableMap.put("BISON", "bison");
		executableMap.put("RELAP-7", "relap-7");
		executableMap.put("RAVEN", "raven");
		executableMap.put("MOOSE_TEST", "moose_test");
		executableMap.put(yamlSyntaxGenerator, yamlSyntaxGenerator);

		// Create the command that will launch the MOOSE product
		String launchCommand = null;
		if ("MOOSE_TEST".equals(executable)) {
			launchCommand = "${installDir}" + "moose/test/" 
					+ executableMap.get(executable) 
					+ "-opt -i ${inputFile} --no-color";
		} else if (yamlSyntaxGenerator.equals(executable)) {
			// Disable the input file appending functionality thingy because
			// this is just a script
			setAppendInputFlag(false);
			
			// Disable input file uploading
			if (yamlSyntaxGenerator.equals(execName)) {
				setUploadInputFlag(false);
			}
			
			launchCommand = 
					// BISON files					
					"if [ -d ${installDir}bison ] "
					+ "&& [ -f ${installDir}bison/bison-opt ]; then;"
					+ "    ${installDir}bison/bison-opt --yaml > bison.yaml;"
					+ "    ${installDir}bison/bison-opt --syntax > bison.syntax;"
					+ "    echo 'Generating BISON files';" 
					+ "fi;"
					// MARMOT files
					+ "if [ -d ${installDir}marmot ] "
					+ "&& [ -f ${installDir}marmot/marmot-opt ]; then;"
					+ "    ${installDir}marmot/marmot-opt --yaml > marmot.yaml;"
					+ "    ${installDir}marmot/marmot-opt --syntax > marmot.syntax;"
					+ "    echo 'Generating MARMOT files';" 
					+ "fi;"			
					// RELAP-7 files
					+ "if [ -d ${installDir}relap-7 ] "
					+ "&& [ -f ${installDir}relap-7/relap-7-opt ]; then;"
					+ "    ${installDir}relap-7/relap-7-opt --yaml > relap.yaml;"
					+ "    ${installDir}relap-7/relap-7-opt --syntax > relap.syntax;"
					+ "    echo 'Generating RELAP-7 files';" 
					+ "elif [ -d ${installDir}r7_moose ] " // Old name
					+ "&& [ -f ${installDir}r7_moose/r7_moose-opt ]; then;"
					+ "    ${installDir}r7_moose/r7_moose-opt --yaml > relap.yaml;"
					+ "    ${installDir}r7_moose/r7_moose-opt --syntax > relap.syntax;"
					+ "    echo 'Generating RELAP-7 files';"
					+ "fi;"
					// RAVEN files
					+ "if [ -d ${installDir}raven ] "
					+ "&& [ -f ${installDir}raven/RAVEN-opt ]; then;"
					+ "    ${installDir}raven/RAVEN-opt --yaml > raven.yaml;"
					+ "    ${installDir}raven/RAVEN-opt --syntax > raven.syntax;"
					+ "    echo 'Generating RAVEN files';" 
					+ "fi;";
		} else if ("RAVEN".equals(executable)) {
			// RAVEN directory is lowercase, but the executable is uppercase
			launchCommand = "${installDir}" + executableMap.get(executable)
					+ "/" + executable + "-opt -i ${inputFile} --no-color";
	
		} else {
			// BISON, MARMOT and RELAP-7 following the same execution pattern
			launchCommand = "${installDir}" + executableMap.get(executable)
					+ "/" + executableMap.get(executable)
					+ "-opt -i ${inputFile} --no-color";
		}

		return launchCommand;
	}

	/**
	 * Sets the information that identifies the Item.
	 */
	protected void setupItemInfo() {

		// Local declarations
		String description = "The Multiphysics Object-Oriented Simulation "
				+ "Environment (MOOSE) is a multiphysics framework developed "
				+ "by Idaho National Laboratory.";

		// Set the model defaults
		setName(MOOSELauncherBuilder.name);
		setDescription(description);
		setItemBuilderName(MOOSELauncherBuilder.name);

		return;
	}

	/**
	 * This operation overrides Item.reviewEntries(). This override is required
	 * in the event that the BISON executable is chosen, in which case
	 * additional files (mesh, power history, peaking factors) will need to be
	 * specified by the client. This method will toggle the additional input
	 * file menus on and off depending on the selected executable.
	 * 
	 * @param preparedForm
	 *            The Form to review.
	 * @return The Form's status.
	 */
	@Override
	protected FormStatus reviewEntries(Form preparedForm) {

		// Local declaration
		FormStatus retStatus = null;

		// Call the super's status review first
		retStatus = super.reviewEntries(preparedForm);

		// If the super's status review was successful, keep going
		if (!retStatus.equals(FormStatus.InfoError)) {

			// Grab the DataComponent in the from that lists available
			// executables
			DataComponent execDataComp = (DataComponent) preparedForm
					.getComponent(5);

			if (execDataComp != null) {
				// Grab the name of the current executable selected by the client
				execName = execDataComp.retrieveAllEntries().get(0)
						.getValue();
			}

			// Check the DataComponent is valid
			if ("Available Executables".equals(execDataComp.getName())) {

				// If the current executable is BISON, remove RAVEN inputs (if
				// any) and specify additional fuel files will need to be added 
				// to the form.
				if ("BISON".equals(execName)) {

					// Remove RAVEN input files (does nothing if types don't 
					// exist)
					removeInputType("Control Logic");
					
					// Add new input types (does nothing if types already exist)
					addInputType("Input File", "inputFile", 
							"MOOSE input file that defines the problem.", 
							".i");
					addInputType("Mesh", "meshFile", "Fuel pin mesh file.",
							".e");
					addInputType(
							"Power History", "powerHistoryFile",
							"Input file containing average rod input power "
							+ "over time.", ".csv");
					addInputType("Peaking Factors", "peakingFactorsFile",
							"An input file containing the axial power profile "
							+ "as a function of time.", ".csv");
					addInputType("Clad Wall Temp", "cladTempFile",
							"Input file containing cladding wall temperature "
							+ "data.", ".csv");
					addInputType("Fast Neutron Flux", "fastFluxFile", "Input "
							+ "file containing fast neutron flux data.", 
							".csv");

					
				} else if ("RAVEN".equals(execName)) {

					// Remove BISON input files (if any)
					removeInputType("Mesh");
					removeInputType("Power History");
					removeInputType("Peaking Factors");
					removeInputType("Clad Wall Temp");
					removeInputType("Fast Neutron Flux");

					// Add new input types (if any)
					addInputType("Input File", "inputFile", 
							"The MOOSE input file that defines the problem.", 
							".i");
					addInputType("Control Logic", "logicFile", "Python control "
							+ "logic input file.", ".py");

					
				} else if (yamlSyntaxGenerator.equals(execName)) {
				
					// Remove any extra input files (if any)
					removeInputType("Input File");
					removeInputType("Mesh");
					removeInputType("Power History");
					removeInputType("Peaking Factors");
					removeInputType("Control Logic");
					removeInputType("Clad Wall Temp");
					removeInputType("Fast Neutron Flux");
					
				} else {

					// Remove any extra input files (if any)
					removeInputType("Mesh");
					removeInputType("Power History");
					removeInputType("Peaking Factors");
					removeInputType("Control Logic");
					removeInputType("Clad Wall Temp");
					removeInputType("Fast Neutron Flux");
					
					// Add input file (if necessary)
					addInputType("Input File", "inputFile", 
							"The MOOSE input file that defines the problem.", 
							".i");
				}

			}

			else {
				retStatus = FormStatus.InfoError;
			}
		}

		return retStatus;
	}

	/**
	 * Override of the JobLauncher.updateResourceComponent() method to also
	 * process the downloaded *.yaml and *.syntax files after the super method
	 * is executed. Any extraneous header/footer text is removed, and the 
	 * resulting file is placed in the default/MOOSE folder (which is created, 
	 * if it doesn't already exist). Any old *.yaml and *.syntax files in the
	 * MOOSE directory will be overwritten.
	 */
	@Override
	protected void updateResourceComponent() {

		// Call the super
		super.updateResourceComponent();

		// If this is the YAML/action syntax process, we need a few extra steps
		if (yamlSyntaxGenerator.equals(execName)) {

			if (project != null && project.isAccessible()) {

				String fileName = "";
				try {
					// Get the MOOSE folder
					IFolder mooseFolder = project.getFolder("MOOSE");

					// Check if the MOOSE folder exists; create it if it doesn't
					if (!mooseFolder.exists()) {
						mooseFolder.create(true, true, null);
					}					
					
					// Get the files in the default folder
					IResource[] resources = project.members();

					// Check the resources and retrieve the .yaml and .syntax
					// files
					for (IResource resource : resources) {

						// Get the filename of the current resource
						fileName = resource.getProjectRelativePath()
								.lastSegment();

						// If the file is *.yaml or *.syntax
						if (resource.getType() == IResource.FILE
								&& (fileName.contains(".yaml") || fileName
										.contains(".syntax"))) {

							// Clean the file of excess headers/footers and
							// move it into the MOOSE directory
							createCleanMOOSEFile(
									resource.getLocation().toOSString());
						}
					}
				} catch (CoreException | IOException e) {
					// Complain
					e.printStackTrace();
				}

			}

			// Refresh the project
			try {
				project.refreshLocal(IResource.DEPTH_INFINITE, null);
			} catch (CoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	}
	
	/**
	 * This method is intended to take a filePath corresponding to a MOOSE YAML
	 * or action syntax file, and remove any extraneous header or footer lines
	 * that aren't valid syntax. If any lines from the file were removed, it
	 * re-writes the file. If no changes were made (no header/footer to remove),
	 * it does nothing.
	 * 
	 * @param filePath	The filepath to the YAML or action syntax file.
	 * @throws IOException
	 * @throws CoreException 
	 */
	private void createCleanMOOSEFile(String filePath) throws 
			IOException, CoreException {
		
		// Local declarations
		String fileExt, execName, fileType = null;
		boolean hasHeader = false, hasFooter = false;
		int headerLine = 0, footerLine = 0;
		String separator = System.getProperty("file.separator");
		ArrayList<String> fileLines;
		
		// Check if the MOOSE folder exists; create it if it doesn't
		IFolder mooseFolder = project.getFolder("MOOSE");

		// If the MOOSE folder doesn't exist, create it
		if (!mooseFolder.exists()) {
			mooseFolder.create(true, true, null);
		}
		
		// Define where the "clean" MOOSE file will be written
		fileExt = filePath.substring(filePath.lastIndexOf("."));
		execName = filePath.substring(filePath.lastIndexOf(separator) + 1, 
				filePath.lastIndexOf(fileExt));
		String cleanFilePath = 
				filePath.substring(0, filePath.lastIndexOf(separator)) 
				+ separator + "MOOSE" + separator + execName + fileExt;				

		if (".yaml".equals(fileExt)) {
			fileType = "YAML";
		} else if (".syntax".equals(fileExt)) {
			fileType = "SYNTAX";
		} else {
			System.out.println("MOOSEFileHandler message: File does not have "
					+ "vaid file extension. Must be .yaml or .syntax but is "
					+ fileExt);
		}

		// Read in the MOOSE file into an ArrayList of Strings
		java.nio.file.Path readPath = Paths.get(filePath);
		fileLines = (ArrayList<String>) 
				Files.readAllLines(readPath, Charset.defaultCharset());
		
		// Define what the header/footer lines look like
		String header = "**START " + fileType + " DATA**";
		String footer = "**END " + fileType + " DATA**";
		
		// Determine if there is a header and/or footer
		hasHeader = fileLines.contains(header);
		hasFooter = fileLines.contains(footer);
		
		// Cut off the footer, if there is one
		if (hasFooter) {
			
			// Record the line number of the footer
			footerLine = fileLines.indexOf(footer);
			
			// Remove the footer line and anything after it
			int i = footerLine;
			while (i < fileLines.size()) {
				fileLines.remove(i);
			}
		}
		
		// Cut off the header, if there is one
		if (hasHeader) {
			
			// Record the line number
			headerLine = fileLines.indexOf(header);
			
			// Remove the header line and anything before it
			for (int i = headerLine; i >= 0; i--) {
				fileLines.remove(i);
			}
		}
		
		// If there was any changes made to the file, write it out and replace
		// the original one
		if (hasHeader || hasFooter) {
			
			// Write out to the clean file now
			java.nio.file.Path writePath = Paths.get(cleanFilePath);
			Files.write(writePath, fileLines, Charset.defaultCharset(), 
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			
			// Delete the old file
			File oldFile = new File(filePath);
			oldFile.delete();
		}
		
		return;
	}
	
}