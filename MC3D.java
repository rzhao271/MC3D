// MC3D.java
// (c)2006 David Vanderschel

import java.applet.*;
import java.awt.event.*;
import java.awt.*;

public class MC3D extends Applet {

  String      startMethod;
  StartButton startButton;
  Puzzle      puzzle = null;
  String args[] = new String[0];

  public void init() {
    startMethod = getParameter( "startMethod" );
    if( startMethod.equals("go") ) {
      puzzle  = new Puzzle( "go", args );
    } else {
      startButton = new StartButton();
      setLayout( new BorderLayout() );
      add( startButton, BorderLayout.CENTER );
    }
  }

  class StartButton extends Button implements ActionListener {
    StartButton() {
      super( "Launch MC3D" );
      addActionListener(this);
    }
    public void actionPerformed( ActionEvent e ) {
      if( puzzle == null  ||  puzzle.frame == null ) puzzle = new Puzzle( "button", args );
      else puzzle.frame.setVisible(true);
    }
  }
}
