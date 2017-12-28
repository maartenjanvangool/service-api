package com.epam.ta.reportportal.plugin;

import com.epam.ta.reportportal.database.dao.LaunchRepository;
import com.epam.ta.reportportal.database.dao.UserRepository;
import com.google.common.util.concurrent.AbstractIdleService;
import org.apache.felix.framework.FrameworkFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginBox extends AbstractIdleService {

	@Autowired
	private UserRepository repos;

	private Framework framework;

	@Override
	protected void startUp() throws Exception {

		FrameworkFactory frameworkFactory = new FrameworkFactory();
		Map<String, String> config = new HashMap<>();

		// make sure the cache is cleaned
		config.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);

		framework = frameworkFactory.newFramework(config);
		framework.init();

		// (10) Start the framework.
		framework.start();

		for (Bundle bundle : getBundles()) {
			bundle.start();
		}

		framework.getBundleContext().registerService(UserRepository.class, repos, null);

		System.out.println(framework.getBundleContext().getService(framework.getBundleContext().getServiceReference(UserRepository.class)).findAll());
		System.out.println("Started");
	}

	public void install(String location) throws BundleException {
		this.framework.getBundleContext().installBundle(location);
	}

	@Override
	protected void shutDown() throws Exception {
		framework.stop();
		framework.waitForStop(0);
	}

	public List<Bundle> getBundles() {
		return Arrays.asList(framework.getBundleContext().getBundles());
	}

	public static void main(String[] args) {
		PluginBox pb = new PluginBox();
		pb.startAsync().awaitRunning();
		System.out.println(pb.getBundles());
		pb.awaitTerminated();
	}
}
