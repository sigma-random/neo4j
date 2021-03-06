/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.desktop.ui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import javax.swing.*;

import org.neo4j.desktop.model.DesktopModel;
import org.neo4j.desktop.model.LastLocation;
import org.neo4j.desktop.model.SysTrayListener;
import org.neo4j.desktop.model.exceptions.UnsuitableDirectoryException;
import org.neo4j.desktop.runtime.DatabaseActions;

import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.OK_OPTION;
import static javax.swing.SwingUtilities.invokeLater;
import static org.neo4j.desktop.ui.Components.createPanel;
import static org.neo4j.desktop.ui.Components.createUnmodifiableTextField;
import static org.neo4j.desktop.ui.Components.createVerticalSpacing;
import static org.neo4j.desktop.ui.Components.ellipsis;
import static org.neo4j.desktop.ui.Components.withBoxLayout;
import static org.neo4j.desktop.ui.Components.withFlowLayout;
import static org.neo4j.desktop.ui.Components.withLayout;
import static org.neo4j.desktop.ui.Components.withSpacingBorder;
import static org.neo4j.desktop.ui.Components.withTitledBorder;
import static org.neo4j.desktop.ui.DatabaseStatus.STARTED;
import static org.neo4j.desktop.ui.DatabaseStatus.STOPPED;
import static org.neo4j.desktop.ui.Graphics.loadImage;
import static org.neo4j.desktop.ui.ScrollableOptionPane.showWrappedConfirmDialog;

/**
 * The main window of the Neo4j Desktop. Able to start/stop a database as well as providing access to some
 * advanced configuration options, such as heap size and database properties.
 */

public class MainWindow extends JFrame
{
    private final DesktopModel model;

    private JPanel rootPanel;

    private JButton optionsButton;
    private JButton browseButton;
    private JButton startButton;
    private JButton stopButton;

    private JTextField directoryDisplay;

    private JPanel statusPanel;
    private CardLayout statusPanelLayout;

    private SystemOutDebugWindow debugWindow;

    private SysTray sysTray;

    private DatabaseActions databaseActions;

    private DatabaseStatus databaseStatus;

    public MainWindow( DatabaseActions databaseActions, DesktopModel model )
    {
        super( "Neo4j Community Edition" );

        this.model = model;
        this.databaseActions = databaseActions;
        this.debugWindow = new SystemOutDebugWindow();

        createComponents();
        setupComponents();

        updateStatus( STOPPED );

        try
        {
            model.setDatabaseDirectory( new File( LastLocation.getLastLocation( model.getDatabaseDirectory().getAbsolutePath() ) ) );
        }
        catch ( UnsuitableDirectoryException ude )
        {
            showWrappedConfirmDialog( this, "Please choose a different folder." + "\n" + ude.getStackTrace(),
                    "Invalid folder selected", OK_OPTION, ERROR_MESSAGE );
        }
    }

    private JPanel createRootPanel( JTextField directoryDisplay, JButton browseButton, Component statusPanel,
                                    JButton startButton, JButton stopButton, JButton settingsButton )
    {
        return withSpacingBorder( withBoxLayout( BoxLayout.Y_AXIS,
            createPanel( createLogoPanel(), createSelectionPanel( directoryDisplay, browseButton ), statusPanel,
                         createVerticalSpacing(), createActionPanel( startButton, stopButton, settingsButton ) ) ) );
    }

    private void createComponents()
    {
        directoryDisplay = createUnmodifiableTextField( LastLocation.getLastLocation( model.getDatabaseDirectory().getAbsolutePath() ), 35 );

        optionsButton = createOptionsButton();
        browseButton = createBrowseButton();
        startButton = createStartButton();
        stopButton = createStopButton();

        statusPanelLayout = new CardLayout();
        statusPanel = createStatusPanel( statusPanelLayout );

        rootPanel = createRootPanel( directoryDisplay, browseButton, statusPanel, startButton, stopButton, optionsButton );

    }

    private void setupComponents()
    {
        setIconImages( Graphics.loadIcons() );
        sysTray = new SysTray( new SysTrayHandler() );

        add( rootPanel );
        pack();
        setResizable( false );
    }

    public void display()
    {
        setLocationRelativeTo( null );
        setVisible( true );
    }

    private JPanel createLogoPanel()
    {
        return withFlowLayout( FlowLayout.LEFT, createPanel( new JLabel( new ImageIcon( loadImage( Graphics.LOGO ) ) ),
                new JLabel( model.getNeo4jVersion() ) ) );
    }

    private JPanel createActionPanel( JButton startButton, JButton stopButton, JButton settingsButton )
    {
        return withBoxLayout( BoxLayout.LINE_AXIS,
                createPanel( settingsButton, Box.createHorizontalGlue(), stopButton, startButton ) );
    }

    private JButton createOptionsButton()
    {
        return Components.createTextButton( ellipsis( "Options" ), new ActionListener()
        {
            @Override
            public void actionPerformed( ActionEvent e )
            {
                JDialog settingsDialog = new SettingsDialog( MainWindow.this, model );
                settingsDialog.setLocationRelativeTo( null );
                settingsDialog.setVisible( true );
            }
        } );
    }

    private JPanel createSelectionPanel( JTextField directoryDisplay, JButton selectButton )
    {
        return withTitledBorder( "Database Location", withBoxLayout( BoxLayout.LINE_AXIS,
                createPanel( directoryDisplay, selectButton ) ) );
    }

    protected void shutdown()
    {
        databaseActions.stop();
        debugWindow.dispose();
        this.dispose();

        System.exit( 0 );
    }

    private JPanel createStatusPanel( CardLayout statusPanelLayout )
    {
        JPanel panel = withLayout( statusPanelLayout, withTitledBorder( "Status", createPanel() ) );

        for ( DatabaseStatus status : DatabaseStatus.values() )
        {
            panel.add( status.name(), status.display( model ) );
        }

        panel.addMouseListener( new MouseAdapter()
        {
            @Override
            public void mouseClicked( MouseEvent e )
            {
                if ( MouseEvent.BUTTON1 == e.getButton() && e.isAltDown() )
                {
                    debugWindow.show();
                }
            }
        } );
        return panel;
    }

    private JButton createBrowseButton()
    {
        ActionListener actionListener = new BrowseForDatabaseActionListener( this, directoryDisplay, model );
        return Components.createTextButton( ellipsis( "Choose" ), actionListener );
    }

    private JButton createStartButton()
    {
        return Components.createTextButton( "Start", new StartDatabaseActionListener( this, model, databaseActions ) );
    }

    private JButton createStopButton()
    {
        return Components.createTextButton( "Stop", new ActionListener()
        {
            @Override
            public void actionPerformed( ActionEvent e )
            {
                updateStatus( DatabaseStatus.STOPPING );

                invokeLater( new Runnable()
                {
                    @Override
                    public void run()
                    {
                        databaseActions.stop();
                        updateStatus( STOPPED );
                    }
                } );
            }
        } );
    }

    public void updateStatus( DatabaseStatus status )
    {
        browseButton.setEnabled( STOPPED == status );
        startButton.setEnabled( STOPPED == status );
        stopButton.setEnabled( STARTED == status );

        statusPanelLayout.show( statusPanel, status.name() );
        sysTray.changeStatus( status );
        databaseStatus = status;
    }

    private class SysTrayHandler implements SysTrayListener
    {
        @Override
        public void open()
        {
            display();
        }

        @Override
        public void exit()
        {
            shutdown();
        }
    }
}
