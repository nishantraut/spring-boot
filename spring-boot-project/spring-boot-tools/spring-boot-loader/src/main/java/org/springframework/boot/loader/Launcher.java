/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.loader;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.jar.JarFile;

/**
 * Base class for launchers that can start an application with a fully configured
 * classpath backed by one or more {@link Archive}s.
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @since 1.0.0
 */
public abstract class Launcher {

	/**
	 * Launch the application. This method is the initial entry point that should be
	 * called by a subclass {@code public static void main(String[] args)} method.
	 * @param args the incoming arguments
	 * @throws Exception if the application fails to launch
	 */
	protected void launch(String[] args) throws Exception {
		if (supportsNestedJars()) {
			JarFile.registerUrlProtocolHandler();
		}
		ClassLoader classLoader = createClassLoader(getClassPathArchivesIterator());
		launch(args, getMainClass(), classLoader);
	}

	/**
	 * Create a classloader for the specified archives.
	 * @param archives the archives
	 * @return the classloader
	 * @throws Exception if the classloader cannot be created
	 * @deprecated since 2.3.0 in favor of {@link #createClassLoader(Iterator)}
	 */
	@Deprecated
	protected ClassLoader createClassLoader(List<Archive> archives) throws Exception {
		return createClassLoader(archives.iterator());
	}

	/**
	 * Create a classloader for the specified archives.
	 * @param archives the archives
	 * @return the classloader
	 * @throws Exception if the classloader cannot be created
	 * @since 2.3.0
	 */
	protected ClassLoader createClassLoader(Iterator<Archive> archives) throws Exception {
		List<URL> urls = new ArrayList<>(50);
		while (archives.hasNext()) {
			urls.add(archives.next().getUrl());
		}
		return createClassLoader(urls.toArray(new URL[0]));
	}

	/**
	 * Create a classloader for the specified URLs.
	 * @param urls the URLs
	 * @return the classloader
	 * @throws Exception if the classloader cannot be created
	 */
	protected ClassLoader createClassLoader(URL[] urls) throws Exception {
		if (supportsNestedJars()) {
			return new LaunchedURLClassLoader(urls, getClass().getClassLoader());
		}
		return new URLClassLoader(urls, getClass().getClassLoader());
	}

	/**
	 * Launch the application given the archive file and a fully configured classloader.
	 * @param args the incoming arguments
	 * @param mainClass the main class to run
	 * @param classLoader the classloader
	 * @throws Exception if the launch fails
	 */
	protected void launch(String[] args, String mainClass, ClassLoader classLoader) throws Exception {
		Thread.currentThread().setContextClassLoader(classLoader);
		createMainMethodRunner(mainClass, args, classLoader).run();
	}

	/**
	 * Create the {@code MainMethodRunner} used to launch the application.
	 * @param mainClass the main class
	 * @param args the incoming arguments
	 * @param classLoader the classloader
	 * @return the main method runner
	 */
	protected MainMethodRunner createMainMethodRunner(String mainClass, String[] args, ClassLoader classLoader) {
		return new MainMethodRunner(mainClass, args);
	}

	/**
	 * Returns the main class that should be launched.
	 * @return the name of the main class
	 * @throws Exception if the main class cannot be obtained
	 */
	protected abstract String getMainClass() throws Exception;

	/**
	 * Returns the archives that will be used to construct the class path.
	 * @return the class path archives
	 * @throws Exception if the class path archives cannot be obtained
	 * @since 2.3.0
	 */
	protected Iterator<Archive> getClassPathArchivesIterator() throws Exception {
		return getClassPathArchives().iterator();
	}

	/**
	 * Returns the archives that will be used to construct the class path.
	 * @return the class path archives
	 * @throws Exception if the class path archives cannot be obtained
	 * @deprecated since 2.3.0 in favor of implementing
	 * {@link #getClassPathArchivesIterator()}.
	 */
	@Deprecated
	protected List<Archive> getClassPathArchives() throws Exception {
		throw new IllegalStateException("Unexpected call to getClassPathArchives()");
	}

	protected final Archive createArchive() throws Exception {
		ProtectionDomain protectionDomain = getClass().getProtectionDomain();
		CodeSource codeSource = protectionDomain.getCodeSource();
		URI location = (codeSource != null) ? codeSource.getLocation().toURI() : null;
		String path = (location != null) ? location.getSchemeSpecificPart() : null;
		if (path == null) {
			throw new IllegalStateException("Unable to determine code source archive");
		}
		File root = new File(path);
		if (!root.exists()) {
			throw new IllegalStateException("Unable to determine code source archive from " + root);
		}
		return (root.isDirectory() ? new ExplodedArchive(root) : new JarFileArchive(root));
	}

	/**
	 * Returns if the launcher needs to support fully nested JARs. If this method returns
	 * {@code false} then only regular JARs are supported and the additional URL and
	 * ClassLoader support infrastructure will not be installed.
	 * @return if nested JARs are supported
	 */
	protected boolean supportsNestedJars() {
		return true;
	}

}
