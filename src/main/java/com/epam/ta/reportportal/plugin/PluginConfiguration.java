package com.epam.ta.reportportal.plugin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PluginConfiguration {

	@Autowired
	private AutowireCapableBeanFactory context;

	@Bean
	public PluginBox pluginBox() {
		PluginBox pluginBox = new PluginBox();
		context.autowireBean(pluginBox);
		pluginBox.startAsync().awaitRunning();
		return pluginBox;
	}
}
