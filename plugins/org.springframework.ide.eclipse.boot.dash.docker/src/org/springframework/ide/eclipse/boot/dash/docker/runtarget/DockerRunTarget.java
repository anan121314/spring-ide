package org.springframework.ide.eclipse.boot.dash.docker.runtarget;

import java.util.Collection;
import java.util.List;

import org.springframework.ide.eclipse.boot.dash.api.App;
import org.springframework.ide.eclipse.boot.dash.model.AbstractRunTarget;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.model.remote.GenericRemoteBootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RemoteRunTarget;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.ListContainersParam;
import com.spotify.docker.client.messages.Container;

public class DockerRunTarget extends AbstractRunTarget<DockerTargetParams> implements RemoteRunTarget<DockerClient, DockerTargetParams> {

	LiveVariable<DockerClient> client = new LiveVariable<>();
	
	public DockerRunTarget(DockerRunTargetType type, DockerTargetParams params, DockerClient client) {
		super(type, params.getUri());
		this.client.setValue(client);
	}

	@Override
	public GenericRemoteBootDashModel<?, ?> createSectionModel(BootDashViewModel parent) {
		return new GenericRemoteBootDashModel<>(this, parent);
	}
	
	@Override
	public boolean canRemove() {
		return true;
	}

	@Override
	public boolean canDeployAppsTo() {
		return true;
	}

	@Override
	public boolean canDeployAppsFrom() {
		return false;
	}

	@Override
	public DockerTargetParams getParams() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public LiveExpression<DockerClient> getClientExp() {
		return client;
	}

	@Override
	public Collection<App> fetchApps() throws Exception {
		
		DockerClient client = this.client.getValue();
		if (client!=null) {
			List<Container> listContainers = client.listContainers(ListContainersParam.allContainers());
			Builder<App> builder = ImmutableList.builder();
			for (Container container : listContainers) {
				builder.add(new DockerApp(container));
			}
			return builder.build();
		}
		
		return ImmutableList.of();
	}

	@Override
	public synchronized void disconnect() {
		DockerClient c = client.getValue();
		if (c!=null) {
			client.setValue(null);
			c.close();
		}
	}

	@Override
	public void connect(ConnectMode mode) throws Exception {
		throw new UnsupportedOperationException("Not yet implemented");
		// TODO Auto-generated method stub
		
	}
}
