package com.taxilink.app;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.validation.HostValidator;

public class TaxiLinkCarAppService extends CarAppService {
    @NonNull
    @Override
    public HostValidator createHostValidator() {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        return new Session() {
            @NonNull
            @Override
            public Screen onCreateScreen(@NonNull Intent intent) {
                return new TaxiLinkCarScreen(this);
            }
        };
    }

    private static class TaxiLinkCarScreen extends Screen {
        TaxiLinkCarScreen(@NonNull Session session) {
            super(session.getCarContext());
        }

        @NonNull
        @Override
        public Template onGetTemplate() {
            UserSession session = new UserSession(getCarContext());
            if (!session.isAndroidAutoTaximeterEnabled()) {
                Pane pane = new Pane.Builder()
                        .addRow(new Row.Builder().setTitle("Taxímetro desactivado").addText("Actívalo en TaxiLink > Perfil y configuración.").build())
                        .build();
                return new PaneTemplate.Builder(pane).setTitle("TaxiLink").setHeaderAction(Action.APP_ICON).build();
            }
            ItemList rows = new ItemList.Builder()
                    .addItem(new Row.Builder().setTitle("TaxiLink Taxímetro").addText("Listo para servicio").build())
                    .addItem(new Row.Builder().setTitle("Tarifas").addText("T1, T2, T3 y aeropuerto con suplementos").build())
                    .addItem(new Row.Builder().setTitle("Aproximación").addText("La app calcula siempre al alza").build())
                    .build();
            Pane pane = new Pane.Builder().addRow(new Row.Builder().setTitle("Central " + session.getCentralNumber()).addText("GPS y servicios desde Firebase").build()).setImage(CarIcon.APP_ICON).build();
            return new PaneTemplate.Builder(pane)
                    .setTitle("TaxiLink")
                    .setHeaderAction(Action.APP_ICON)
                    .build();
        }
    }
}
