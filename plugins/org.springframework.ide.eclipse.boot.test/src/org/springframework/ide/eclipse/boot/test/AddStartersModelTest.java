/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.ide.eclipse.boot.test.BootProjectTestHarness.bootVersion;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.ide.eclipse.boot.core.ISpringBootProject;
import org.springframework.ide.eclipse.boot.core.SpringBootStarters;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrProjectDownloader;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrService;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.Dependency;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrServiceSpec.Option;
import org.springframework.ide.eclipse.boot.core.initializr.InitializrUrl;
import org.springframework.ide.eclipse.boot.test.InitializrWizardModelHarness.MockInitializrService;
import org.springframework.ide.eclipse.boot.test.util.TestBracketter;
import org.springframework.ide.eclipse.boot.test.util.TestResourcesUtil;
import org.springframework.ide.eclipse.boot.wizard.CheckBoxesSection.CheckBoxModel;
import org.springframework.ide.eclipse.boot.wizard.HierarchicalMultiSelectionFieldModel;
import org.springframework.ide.eclipse.boot.wizard.starters.AddStartersCompareModel;
import org.springframework.ide.eclipse.boot.wizard.starters.AddStartersCompareResult;
import org.springframework.ide.eclipse.boot.wizard.starters.AddStartersError;
import org.springframework.ide.eclipse.boot.wizard.starters.AddStartersInitializrService;
import org.springframework.ide.eclipse.boot.wizard.starters.AddStartersPreferences;
import org.springframework.ide.eclipse.boot.wizard.starters.AddStartersWizardModel;
import org.springframework.ide.eclipse.boot.wizard.starters.InitializrModel;
import org.springsource.ide.eclipse.commons.frameworks.core.downloadmanager.URLConnectionFactory;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.util.Log;
import org.springsource.ide.eclipse.commons.tests.util.StsTestUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSet;

@SuppressWarnings("restriction")
public class AddStartersModelTest {

	private BootProjectTestHarness harness = new BootProjectTestHarness(ResourcesPlugin.getWorkspace());
	private IPreferenceStore prefs = new MockPrefsStore();


	private static final String MOCK_VALID_INITIALIZR_URL = "https://add.starters.start.spring.io";
	private static final String[] SUPPORTED_BOOT_VERSIONS = new String[] {"2.3.1.RELEASE","2.2.7.RELEASE", "2.1.14.RELEASE"};
	private static final String CURRENT_BOOT_VERSION = "2.3.1.RELEASE";
	// A starter zip file containing "selected" web and actuator dependencies
	private static final String STARTER_ZIP_WEB_ACTUATOR = "/initializr/boot-web-actuator/starter.zip";

	private static boolean wasAutobuilding;

	@BeforeClass
	public static void setupClass() throws Exception {
		wasAutobuilding = StsTestUtil.isAutoBuilding();
		StsTestUtil.setAutoBuilding(false);
	}

	@AfterClass
	public static void teardownClass() throws Exception {
		StsTestUtil.setAutoBuilding(wasAutobuilding);
	}

	@Before
	public void setup() throws Exception {
		StsTestUtil.cleanUpProjects();
	}

	@Rule
	public TestBracketter testBracketer = new TestBracketter();

	/**
	 * Tests that the initializr model with dependencies is loaded in the wizard
	 */
	@Test
	public void selectDependenciesInWizard() throws Exception {
		String projectBootVersion = CURRENT_BOOT_VERSION;
		IProject project = harness.createBootProject("selectDependenciesInWizard", bootVersion(projectBootVersion));

		String starterZipFile = STARTER_ZIP_WEB_ACTUATOR;
		String validInitializrUrl = MOCK_VALID_INITIALIZR_URL;
		String[] supportedBootVersions = SUPPORTED_BOOT_VERSIONS;
		String[] dependenciesToSelect = new String[] {"web", "actuator"};
		AddStartersWizardModel wizard = createAndLoadWizard(project, starterZipFile, validInitializrUrl,
				supportedBootVersions, dependenciesToSelect);

		// Verify the fields and model are set in the wizard after loading
		assertEquals(validInitializrUrl, wizard.getServiceUrl().getValue());
		assertEquals(projectBootVersion, wizard.getBootVersion().getValue());
		assertEquals(ValidationResult.OK, wizard.getValidator().getValue());
		assertInitializrAndCompareModelsNotNull(wizard);

		// Ensure dependency info are loaded in the initializr Model
		// and dependencies are selected
		InitializrModel initializrModel = wizard.getInitializrModel().getValue();
		HierarchicalMultiSelectionFieldModel<Dependency> dependencies = initializrModel.dependencies;
		List<CheckBoxModel<Dependency>> allDependencies = dependencies.getAllBoxes();
		assertTrue(!allDependencies.isEmpty());

		// Check that the actual selected dependencies in the wizard match the expected dependencies
		List<Dependency> currentSelection = dependencies.getCurrentSelection();
		assertTrue(currentSelection.size() > 0);
		assertEquals(dependenciesToSelect.length, currentSelection.size());
		for (Dependency selected : currentSelection) {
			assertNotNull(getExpectedDependency(selected, dependenciesToSelect));
		}

		// There should be no comparison, as we haven't downloaded the project to compare to in this test
		AddStartersCompareModel compareModel = wizard.getCompareModel().getValue();
		assertNull(compareModel.getCompareResult().getValue());
	}


	@Test
	public void changeBetweenInvalidAndValidUrl() throws Exception {
		String projectBootVersion = CURRENT_BOOT_VERSION;
		IProject project = harness.createBootProject("changeBetweenInvalidAndValidUrl", bootVersion(projectBootVersion));

		String starterZipFile = STARTER_ZIP_WEB_ACTUATOR;
		String validInitializrUrl = MOCK_VALID_INITIALIZR_URL;
		String[] supportedBootVersions = SUPPORTED_BOOT_VERSIONS;
		String[] dependenciesToSelect = new String[] {"web", "actuator"};
		AddStartersWizardModel wizard = createAndLoadWizard(project, starterZipFile, validInitializrUrl,
				supportedBootVersions, dependenciesToSelect);

		// Verify the fields and model are set in the wizard after loading
		assertEquals(validInitializrUrl, wizard.getServiceUrl().getValue());
		assertEquals(projectBootVersion, wizard.getBootVersion().getValue());
		assertEquals(ValidationResult.OK, wizard.getValidator().getValue());
		assertInitializrAndCompareModelsNotNull(wizard);

		// Set a valid URL that is not a valid initializr URL
		wizard.getServiceUrl().setValue("https://www.google.ca");
		waitForWizardJob();
		// There should be no valid initializr model available
		assertInitializrAndCompareModelsNull(wizard);
		AddStartersError result = (AddStartersError)wizard.getValidator().getValue();
		assertTrue(result.status == IStatus.ERROR);
		assertTrue(result.details.contains("ConnectException"));

		// Set a valid URL again. Should load a valid model and validate
		wizard.getServiceUrl().setValue(validInitializrUrl);
		waitForWizardJob();
		assertInitializrAndCompareModelsNotNull(wizard);
		assertEquals(ValidationResult.OK, wizard.getValidator().getValue());
	}


	@Test
	public void malformedUrlError() throws Exception {
		String projectBootVersion = CURRENT_BOOT_VERSION;
		IProject project = harness.createBootProject("malformedUrlError", bootVersion(projectBootVersion));

		String starterZipFile = STARTER_ZIP_WEB_ACTUATOR;
		String validInitializrUrl = MOCK_VALID_INITIALIZR_URL;
		String[] supportedBootVersions = SUPPORTED_BOOT_VERSIONS;
		String[] dependenciesToSelect = new String[] {"web", "actuator"};
		AddStartersWizardModel wizard = createAndLoadWizard(project, starterZipFile, validInitializrUrl,
				supportedBootVersions, dependenciesToSelect);

		// Verify the fields and model are set in the wizard after loading
		assertEquals(validInitializrUrl, wizard.getServiceUrl().getValue());
		assertEquals(projectBootVersion, wizard.getBootVersion().getValue());
		assertEquals(ValidationResult.OK, wizard.getValidator().getValue());
		assertInitializrAndCompareModelsNotNull(wizard);

		// Set a malformed URL
		wizard.getServiceUrl().setValue("wlwlwlw");
		waitForWizardJob();

		// There should be no valid initializr model available
		assertInitializrAndCompareModelsNull(wizard);
		AddStartersError result = (AddStartersError)wizard.getValidator().getValue();
		assertTrue(result.status == IStatus.ERROR);
		assertTrue(result.details.contains("MalformedURLException"));

		// Set a valid URL again. Should load a valid model and validate
		wizard.getServiceUrl().setValue(validInitializrUrl);
		waitForWizardJob();
		assertEquals(ValidationResult.OK, wizard.getValidator().getValue());
		assertInitializrAndCompareModelsNotNull(wizard);
	}


	@Test
	public void missingUrlError() throws Exception {
		String projectBootVersion = CURRENT_BOOT_VERSION;
		IProject project = harness.createBootProject("missingUrlError", bootVersion(projectBootVersion));

		String starterZipFile = STARTER_ZIP_WEB_ACTUATOR;
		String validInitializrUrl = MOCK_VALID_INITIALIZR_URL;
		String[] supportedBootVersions = SUPPORTED_BOOT_VERSIONS;
		String[] dependenciesToSelect = new String[] {"web", "actuator"};
		AddStartersWizardModel wizard = createAndLoadWizard(project, starterZipFile, validInitializrUrl,
				supportedBootVersions, dependenciesToSelect);

		// Verify the fields and model are set in the wizard after loading
		assertEquals(validInitializrUrl, wizard.getServiceUrl().getValue());
		assertEquals(projectBootVersion, wizard.getBootVersion().getValue());
		assertEquals(ValidationResult.OK, wizard.getValidator().getValue());
		assertInitializrAndCompareModelsNotNull(wizard);

		// Set empty URL
		wizard.getServiceUrl().setValue("");
		waitForWizardJob();

		// There should be no valid initializr model available
		AddStartersError result = (AddStartersError)wizard.getValidator().getValue();
		assertTrue(result.status == IStatus.ERROR);
		assertTrue(result.details.contains("Missing initializr service URL"));
		assertInitializrAndCompareModelsNull(wizard);

		// Set a valid URL again. Should load a valid model and validate
		wizard.getServiceUrl().setValue(validInitializrUrl);
		waitForWizardJob();
		assertEquals(ValidationResult.OK, wizard.getValidator().getValue());
		assertInitializrAndCompareModelsNotNull(wizard);
	}


	@Test
	public void unsupportedBootVersionError() throws Exception {

		// Create a project with a valid boot version as the harness
		// doesn't allow creating a project with old unsupported boot version
		// However we will change the list of supported boot versions to exclude this boot version
		// to simulate a case where there is a unsupported boot version in the add starters wizard
		IProject project = harness.createBootProject("unsupportedBootVersionError", bootVersion(CURRENT_BOOT_VERSION));

		String starterZipFile = STARTER_ZIP_WEB_ACTUATOR;
		String validInitializrUrl = MOCK_VALID_INITIALIZR_URL;

		// List supported versions that do not include the version used to create the project
		String[] supportedBootVersions = new String[] { "4.4.0.RELEASE", "1.1.0.RELEASE", "1.5.3.RELEASE"};

		// No dependencies to select, as in this test, there should be no initializr information because boot version is not supported
		String[] dependenciesToSelect = null;
		AddStartersWizardModel wizard = createAndLoadWizard(project, starterZipFile, validInitializrUrl,
				supportedBootVersions, dependenciesToSelect);

		AddStartersError result = (AddStartersError)wizard.getValidator().getValue();
		assertTrue(result.status == IStatus.ERROR);
		assertTrue(result.details.contains("FileNotFoundException"));

		// Also verify that the error message details lists all the supported boot versions that a user should update to
		assertTrue(result.details.contains("4.4.0.RELEASE"));
		assertTrue(result.details.contains("1.1.0.RELEASE"));
		assertTrue(result.details.contains("1.5.3.RELEASE"));
	}

	@Test
	public void basicComparison() throws Exception {
		String projectBootVersion = CURRENT_BOOT_VERSION;
		IProject project = harness.createBootProject("basicComparison", bootVersion(projectBootVersion));

		String starterZipFile = STARTER_ZIP_WEB_ACTUATOR;
		String validInitializrUrl = MOCK_VALID_INITIALIZR_URL;
		String[] supportedBootVersions = SUPPORTED_BOOT_VERSIONS;
		String[] dependenciesToSelect = new String[] {"web", "actuator"};
		AddStartersWizardModel wizard = createAndLoadWizard(project, starterZipFile, validInitializrUrl,
				supportedBootVersions, dependenciesToSelect);

		assertInitializrAndCompareModelsNotNull(wizard);

		AddStartersCompareModel compareModel = wizard.getCompareModel().getValue();
		AddStartersCompareResult comparison = compareModel.getCompareResult().getValue();
		assertNull(comparison);

		compareModel.createComparison(new NullProgressMonitor());
		comparison = compareModel.getCompareResult().getValue();
		assertNotNull(comparison);
		// Verify that the comparison contains the "mocked" downloaded zip file as a comparison source
		assertTrue(comparison.getDownloadedProject().getPath().contains(starterZipFile));
		// Verify that the comparison contains the local project as a comparison source
		assertTrue(comparison.getLocalResource().getProject().equals(project));
	}


	/*
	 *
	 * Helper methods and mock classes
	 *
	 */

	public static class MockAddStartersInitializrService extends AddStartersInitializrService {


		// Empty URL factory. Shouldn't do anything as an actual URL connection is not
		// needed for testing
		private static final URLConnectionFactory EMPTY_URL_FACTORY = new URLConnectionFactory() {

			@Override
			public URLConnection createConnection(URL url) throws IOException {
				return null;
			}

		};

		private final String starterZipPath;

		private final Set<String> supportedBootVersions;

		private final String validInitializrUrl;

		private final String initializrInfoInput;

		private final String[] expectedSelectedDependencies;

		/**
		 *
		 * Provides the Add Starters wizard with mock versions of any intializr
		 * component or information that would otherwise be downloaded from a real
		 * initializr service, like initializr info containing dependencies to show in
		 * the wizard, as well as a downloaded project from `/starter.zip` endpoint.
		 *
		 * @param starterZipPath               path to an starter zip file representing
		 *                                     a project that would otherwise be
		 *                                     downloaded from an actual initializr
		 *                                     `/starter.zip` endpoint
		 * @param validInitializrUrl           an initializr service URL that is
		 *                                     considered "valid" for the wizard (does
		 *                                     not have to be a real-life initializr URL
		 *                                     as no connection will be attempted), and
		 *                                     read by the wizard when it initially is
		 *                                     created. Mocks having a valid URL like
		 *                                     "https://start.spring.io " in an actual
		 *                                     boot initializr preference that is
		 *                                     initially read by the real wizard when it
		 *                                     opens.
		 * @param supportedBootVersions        list of supported boot versions
		 *                                     associated with the "valid" initializr
		 *                                     URL. This is used by the wizard to check
		 *                                     against the boot version of the local
		 *                                     project. Used for testing error
		 *                                     conditions (e.g. unsupported boot version
		 *                                     errors that a wizard may throw)
		 * @param initializrInfoInput          a JSON test file that contains initializr
		 *                                     info, like all available dependencies,
		 *                                     that would otherwise be downloaded from a
		 *                                     real initializr service.
		 * @param expectedSelectedDependencies List of selected dependencies that are
		 *                                     expected when a request is made to download a project within the wizard mechanics
		 */
		public MockAddStartersInitializrService(String starterZipPath,
				String validInitializrUrl,
				String[] supportedBootVersions,
				String[] expectedSelectedDependencies,
				String initializrInfoInput) {
			super(EMPTY_URL_FACTORY);
			this.starterZipPath = starterZipPath;
			this.validInitializrUrl = validInitializrUrl;
			this.supportedBootVersions = ImmutableSet.copyOf(supportedBootVersions);
			this.initializrInfoInput = initializrInfoInput;
			this.expectedSelectedDependencies = expectedSelectedDependencies != null ? expectedSelectedDependencies : new String[0];
		}

		@Override
		public InitializrService getService(Supplier<String> url) {
			MockInitializrService mockWrappedService = new MockInitializrService() {
				@Override
				public SpringBootStarters getStarters(String bootVersion) throws Exception {
					// Mock unsupported boot version. This is an actual error thrown in the real wizard
					if (!supportedBootVersions.contains(bootVersion)) {
						throw new FileNotFoundException();
					} else {
						return super.getStarters(bootVersion);
					}
				}
			};

			// This sets initializr info from a JSON file that captures data that would
			// otherwise be downloaded from initializr.
			try {
				mockWrappedService.setInputs(initializrInfoInput);
			} catch (Exception e) {
				// print the error so its visible during tests
				e.printStackTrace();
				return null;
			}
			return mockWrappedService;
		}

		@Override
		public InitializrProjectDownloader getProjectDownloader(InitializrUrl url) {
			return new MockProjectDownloader(urlConnectionFactory, url, starterZipPath, expectedSelectedDependencies);
		}

		@Override
		public Option[] getSupportedBootReleaseVersions(String url) throws Exception {

			Builder<Object> options = ImmutableList.builder();
			for (String v : supportedBootVersions) {
				Option option = new Option();
				option.setId(v);
				options.add(option);
			}

			return options.build().toArray(new Option[0]);
		}

		@Override
		public void checkBasicConnection(URL url) throws Exception {
			// Tests an actual error thrown by initializr service: a valid URL (e.g. https://www.google.com) that is
			// not an initializr URL
			if (!validInitializrUrl.equals(url.toString())) {
				throw new ConnectException();
			}
		}

	}

	public class MockAddStartersPreferences extends AddStartersPreferences {

		private final List<String> storedUrls = new ArrayList<>();


		public MockAddStartersPreferences(String validUrl) {
			super(prefs);
			storedUrls.add(validUrl);
		}

		@Override
		public String getInitializrUrl() {
			return storedUrls.get(0);
		}

		@Override
		public String[] getInitializrUrls() {
			return storedUrls.toArray(new String[] {});
		}

		@Override
		public void addInitializrUrl(String url) {
			storedUrls.add(url);
		}

	}

	public static class MockProjectDownloader extends InitializrProjectDownloader {

		private final String starterZipPath;
		private final String[] expectedSelectedDependencies;

		public MockProjectDownloader(URLConnectionFactory urlConnectionFactory, InitializrUrl url,
				String starterZipPath, String[] expectedSelectedDependencies) {
			super(urlConnectionFactory, url);
			this.starterZipPath = starterZipPath;
			this.expectedSelectedDependencies = expectedSelectedDependencies;
		}

		@Override
		public File getProject(List<Dependency> dependencies, ISpringBootProject bootProject) throws Exception {
			// selected dependencies from the wizard don't actually get used in the mock project downloader
			// as we dont actually  need to download the zip file from initializr using a constructed URL that
			// contains the dependencies.
			// However, we can at least test that the dependencies that were selected in the wizard match the expected ones
			assertSelectedDependencies(dependencies);

			return TestResourcesUtil.getTestFile(starterZipPath);
		}

		private void assertSelectedDependencies(List<Dependency> dependencies) {
			assertEquals(expectedSelectedDependencies.length, dependencies.size());
			for (Dependency actual : dependencies) {
				assertNotNull(getExpectedDependency(actual, expectedSelectedDependencies));
			}
		}
	}

	private void loadInitializrModel(AddStartersWizardModel wizard) throws Exception {
		wizard.addModelLoader(() -> wizard.createInitializrModel(new NullProgressMonitor()));
		// Wait for it to finish
		waitForWizardJob();
	}

	private void waitForWizardJob() throws InterruptedException {
		Job.getJobManager().join(AddStartersWizardModel.JOB_FAMILY, null);
	}

	private void assertInitializrAndCompareModelsNull(AddStartersWizardModel wizard) {
		InitializrModel initializrModel = wizard.getInitializrModel().getValue();
		assertNull(initializrModel);
		AddStartersCompareModel compareModel = wizard.getCompareModel().getValue();
		assertNull(compareModel);
	}

	private void assertInitializrAndCompareModelsNotNull(AddStartersWizardModel wizard) {
		InitializrModel initializrModel = wizard.getInitializrModel().getValue();
		assertNotNull(initializrModel);
		AddStartersCompareModel compareModel = wizard.getCompareModel().getValue();
		assertNotNull(compareModel);
	}

	private static String getExpectedDependency(Dependency dep, String... expectedDependencies) {
		for (String expected : expectedDependencies) {
			if (expected.equals(dep.getId())) {
				return expected;
			}
		}
		return null;
	}

	private void selectDependenciesInWizard(AddStartersWizardModel wizard, String...dependencies) {
		InitializrModel initializrModel = wizard.getInitializrModel().getValue();
		for (String dep : dependencies) {
			initializrModel.addDependency(dep);
		}
	}

	/**
	 *
	 * @param project local project to compare to
	 * @param starterZipFile path to a starter.zip file representing a downloaded project to compare to the local one
	 * @param validInitializrUrl an initializr URL to set in preferences that the wizard will consider to be a valid service  URL (no connections will be made though)
	 * @param supportedBootVersions
	 * @param dependenciesToSelect dependencies to select in the wizard once it is created. Pass null if no dedencies to select.
	 * @return
	 * @throws Exception
	 */
	private AddStartersWizardModel createAndLoadWizard(
			IProject project,
			String starterZipFile,
			String validInitializrUrl,
			String[] supportedBootVersions,
			String[] dependenciesToSelect) throws Exception {
		String initializrInfoInput = "sample";
		AddStartersInitializrService initializrService = new MockAddStartersInitializrService(
				starterZipFile,
				validInitializrUrl,
				supportedBootVersions,
				dependenciesToSelect,
				initializrInfoInput);
		AddStartersPreferences preferences = new MockAddStartersPreferences(validInitializrUrl);
		AddStartersWizardModel wizard = new AddStartersWizardModel(project, preferences, initializrService);

		// Wizard model is not available until it is loaded
		assertInitializrAndCompareModelsNull(wizard);

		loadInitializrModel(wizard);

		// Select the dependencies in the "Dependencies" section of the wizard
		if (dependenciesToSelect != null) {
			selectDependenciesInWizard(wizard, dependenciesToSelect);
		}

		return wizard;
	}

	private void performOk(AddStartersWizardModel wizard) throws Exception {
		wizard.performOk();
		waitForWizardJob();
	}

}
