package @{ProjectPackage}.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Label;

public class EntryPoint implements EntryPoint {
    @Override
    public void onModuleLoad() {
        Label lbl = new Label("Hello GWT from @{ProjectName}");
        RootPanel.get().add(lbl);
        Window.alert("GWT module @{GwtModule} loaded");
    }
}
