/* Puzzle.java:  Implementing class for MC3D (Applet) and MC3DApp (application)
 * (c)2005, 2006 David Vanderschel - DvdS@Austin.RR.com or david-v@Texas.Net

 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.

 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.

 *  To view the GNU General Public License, visit the gnu.org Web site
 *  at http://www.gnu.org/licenses/gpl.html.  (If something goes wrong
 *  with that link, check around at fsf.org and gnu.org.  If even that
 *  fails, to obtain a printed copy, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 *  02110-1301, USA.)
 */

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.filechooser.*;
import java.lang.Math;
import java.util.*;
import java.text.*;
import java.beans.*;
import java.io.*;

public class Puzzle {

  Puzzle( String s, String args[] ) {
    startMethod = s;
    puzzleArgs  = args;
    makeFrame();
  }

  String puzzleArgs[];
  String startMethod;


/******** Graphics Pane Setup - Mostly about accepting input. ********/

  MainPane pane;
  InputMap iMap;

  class MainPane extends JPanel implements MouseListener, MouseMotionListener {

    MainPane() {
      super();
      addMouseListener(this);
      addMouseMotionListener(this);
      keyBoardInSetup( getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW ), getActionMap() );
    }

    public void paintComponent( Graphics g1 ) {
      super.paintComponent(g1);
      g = (Graphics2D)g1;            // Save graphics context as a global for everybody.
      paintPane( getSize().width, getSize().height );
    }

    public void mouseClicked ( MouseEvent e ) { }
    public void mouseEntered ( MouseEvent e ) { }
    public void mouseExited  ( MouseEvent e ) { exitMouse(e); }
    public void mousePressed ( MouseEvent e ) { downMouse(e); }
    public void mouseReleased( MouseEvent e ) { upMouse(e);   }
    public void mouseDragged ( MouseEvent e ) { dragMouse(e); }
    public void mouseMoved   ( MouseEvent e ) { moveMouse(e); }
  }


  int modsIE[] = new int[16];       // Array of all 16 possible combinations of below mask bits.
  /* Modifier bits from InputEvent */
  int javaMod[] = { InputEvent.CTRL_DOWN_MASK, InputEvent.ALT_DOWN_MASK,
                    InputEvent.META_DOWN_MASK, InputEvent.SHIFT_DOWN_MASK };
  /* BFUDLR KeyEvent codes */
  char bEvent[] = { KeyEvent.VK_B, KeyEvent.VK_F, KeyEvent.VK_U,
                    KeyEvent.VK_D, KeyEvent.VK_L, KeyEvent.VK_R };
  /* Arrow keys for Rover events. */
  char rEvent[] = { KeyEvent.VK_KP_LEFT, KeyEvent.VK_KP_RIGHT, KeyEvent.VK_KP_UP, KeyEvent.VK_KP_DOWN,
                    KeyEvent.   VK_LEFT, KeyEvent.   VK_RIGHT, KeyEvent.   VK_UP, KeyEvent.   VK_DOWN };
  String arrowDir[] = {"left", "right", "up", "down"}; // Defines indices for the arrow directions.

  /* Request receipt of the various desired key combinations.  Also used in FlatPane setup. */
  void keyBoardInSetup( InputMap iMap, ActionMap actionMap ) {
    int i, j, k, bits;
    for(i=0; i< bEvent.length; i++) {
      for(j=0; j<16; j++) {   // There must be a better way than explicitly doing every combination of mods.
        int mods = 0;         // Convert j's bit pattern to corresponding pattern of InputEvent modifier bits.
        for( k=0, bits=j; k<4; k++, bits = bits>>1 ) if( bits%2 != 0 ) modsIE[j] |= javaMod[k];
        iMap.put( KeyStroke.getKeyStroke( bEvent[i], modsIE[j] ), "twist" );
      }
    }
    for(i=0; i<rEvent.length; i++) {
      iMap.put( KeyStroke.getKeyStroke( rEvent[i], InputEvent. ALT_DOWN_MASK ), arrowDir[i%4] );
      iMap.put( KeyStroke.getKeyStroke( rEvent[i], InputEvent.CTRL_DOWN_MASK ), arrowDir[i%4] );
      iMap.put( KeyStroke.getKeyStroke( rEvent[i], InputEvent.META_DOWN_MASK ), arrowDir[i%4] );
    }
    for(i=0; i<command.length-1; i++) {
      iMap.put( KeyStroke.getKeyStroke( command[i].accKey, InputEvent.CTRL_DOWN_MASK ), command[i].name );
      actionMap.put( command[i].name, command[i].action );
    }
    String actionName[]     = { "left", "right", "up", "down", "twist" };
    AbstractAction action[] = {  left,   right,   up,   down,   twist  };
    for(i=0; i<action.length; i++) actionMap.put( actionName[i], action[i] );
  }

  /* I don't know why I need the separate four actions below.  The problem is that getActionCommand on the
       passed ActionEvent returns null, so I can't tell which arrow was typed without the separate actions. */
  AbstractAction left  = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) { roverKey( 0, e.getModifiers() ); } };
  AbstractAction right = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) { roverKey( 1, e.getModifiers() ); } };
  AbstractAction up    = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) { roverKey( 2, e.getModifiers() ); } };
  AbstractAction down  = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) { roverKey( 3, e.getModifiers() ); } };

  AbstractAction twist = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) { twist(e); } };

  AbstractAction init = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) {
        int i;
        curStack.push( new Permute() );
        for(i=0; i<54; i++) place[i].colorI = i/9;
        paintPanes();
      }
    };

  AbstractAction scramble = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) {
        int i;
        boolean reflect = reflect1;
        curStack.push( new Permute() );
        drawing = false;
        for(i=0; i<100; i++) {
          dirControl = ( random.nextInt(2) == 0 );
          sliceGo =    ( random.nextInt(2) == 0 ) ? posCase : negCase;
          twistAxis  =   random.nextInt(3);
          if( reflect ) reflect2 = ( random.nextInt(3) == 0 );
          twist();
        }
        reflect1 = false;
        drawing  = true;
        paintPanes();
      }
    };

  AbstractAction undo = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) {
        if( animating  &&  twistQueue.n < Q_LEN ) twistQueue.push( new Undo() ); 
        if( animating  ||  undoStack.n == 0 ) return; 
        curStack = redoStack;
          undoStack.pop().doIt();
        curStack = undoStack;
        paintPanes();
      }
    };

  AbstractAction redo = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) {
        if( animating  &&  twistQueue.n < Q_LEN ) twistQueue.push( new Redo() ); 
        if( animating  ||  redoStack.n == 0 ) return; 
        redoStack.pop().doIt();
        paintPanes();
      }
    };

  AbstractAction cancel = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) {
        curStack = trashStack;
          redo.actionPerformed( dummyEvent );
        curStack = undoStack;
      }
    };

  AbstractAction exit = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) {
        aniTimer.stop();
        roverTimer.stop();
        driveTimer.stop();
        if( frame     != null ) frame.dispose();
        if( flatFrame != null ) flatFrame.dispose();
        frame          = null;
        flatFrame      = null;
      }
    };

  /* Action for changes to Check Boxes on View and Toggles Menus. */
  AbstractAction cbChange = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) {
        String s = e.getActionCommand();
        if( s.equals( roverUndoableBox.name ) ) whatsChanging = NOTHING;
        if( s.equals(  viewUndoableBox.name ) ) whatsChanging = NOTHING;
        if( s.equals(     autoScaleBox.name ) ) scaleDone = false;
        if( s.equals(       autoPosBox.name )  &&  autoPosBox.isSelected() ) positionCenters();
        if( s.equals(       layeredBox.name ) ) {
          if( layeredBox.isSelected() ) {
            if( flatFrame != null ) flatFrame.setVisible(true); else makeFlatFrame();
          } else {
            if( flatFrame != null ) flatFrame.setVisible(false);
          }
        }
        paintPanes();
      }
    };

  AbstractAction mirror      = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) { reflect1 = true; } };

  AbstractAction colorAdjust = new AbstractAction() {
      public void actionPerformed( ActionEvent ae ) {
        int     i=0, j;
        String  title;

        String s = JOptionPane.showInputDialog( frame,
              "Enter character for color to change.\nbfudlr: for sticker colors.\n"+
              "BFUDLR: for label colors.\ng/G: for backGround/foreGround\ne/E: for edge colors - 2D/1D",
              "Adjusting a color.", JOptionPane.QUESTION_MESSAGE  );
        if( s==null  ||  s.length()==0 ) return;
        char    c  = s.charAt(0);
        boolean uc = (c<='Z');  // true when character is Upper Case
        if(!uc) c -= 32;        // Transform lower case to upper case.
        if(c=='G') {
          title      = uc ? "Specify new foreground color."     : "Specify new background color.";
          colorChooser.setColor( uc ?    fgColor                :              bgColor          );
        } else if(c=='E') {
          title      = uc ? "Specify new 1D separator color."   : "Specify new 2D edge color."   ;
          colorChooser.setColor( uc ?    edge1Color             :              edge2Color       ); 
        } else {
          for(i=0; i<6; i++) if( fChar[i] == c ) break;
          if(i==6) return;
          title = uc ? "Specify new color for \""+c+"\" label." : "Specify new color for "+c+" face sticker.";
          colorChooser.setColor( uc ?     labelColor[i]         :              faceColor[i]     );
        }
        JColorChooser.createDialog( frame, title, true, colorChooser, newColor, cancelColor ).setVisible(true);
        if( colorChosen == null ) return;

        if(c=='G') {
          if(uc)    fgColor = colorChosen; 
          else      bgColor = colorChosen; 
        } else if(c=='E') {
          if(uc) edge1Color = colorChosen; 
          else   edge2Color = colorChosen; 
        } else if(uc) {
              labelColor[i] = colorChosen;
        } else {
          float f[] = colorChosen.getRGBColorComponents(null);
          for(j=0; j<3; j++) colorComponents[i][j] = (double)f[j];
        }

        createColors( faceColor,   1.f );
        createColors( dimColor1D, .85f );
        lastInFaceBright = 0.f;
        paintPanes();
      }
    };

  /* The extra fooling around with the createDialog method of JColorChooser, as opposed to the simpler 
     showDialog method, is to achieve persistence of the "recent" colors in the dialogue. */
  JColorChooser colorChooser = new JColorChooser();
  Color colorChosen;

  AbstractAction newColor    = new AbstractAction() {
      public void actionPerformed( ActionEvent  e ) { colorChosen = colorChooser.getColor(); } };

  AbstractAction cancelColor = new AbstractAction() { 
      public void actionPerformed( ActionEvent  e ) { colorChosen = null;                    } };


  /* Save State. */
  AbstractAction save        = new AbstractAction() {
      public void actionPerformed( ActionEvent ae ) {
        int i, j;
        if( !startMethod.equals("application") ) return;
        JFileChooser fc = new JFileChooser(".");
        fc.setFileFilter( new Filter() );
        if( fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION ) return;
        File f = fc.getSelectedFile();
        String filePath = fix(f);
        f = new File( filePath );
        if( f.canRead() ) {
          if( JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(
                         pane, "File "+filePath+" already exists.\nDo you want to CANCEL this operation?",
                         "Save-State Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE ) ) {
            JOptionPane.showMessageDialog( pane, "Save aborted.", "State Saving Attempt", 
                                           JOptionPane.WARNING_MESSAGE );
            return;
          }
        }

        try {
          PrintStream ps = new PrintStream( new FileOutputStream(f) );
          ps.println("Current_Parameter: " + curParam);
          for(i=0; i<parButsSub.length; i++) {
            ps.print( parButsSub[i].mc3ID + "  " ); // parameter-group name
            for(j=0; j<parButsSub[i].paramA.length; j++) ps.print( parButsSub[i].paramA[j].val + "  " );
            ps.println();
          }
          ps.print("Frame_Size:  ");
          ps.println( frame.getWidth() + "  " + frame.getHeight() );
          ps.print("Center_Locations:  ");
          ps.println( in_Center.x      + " " + in_Center.y       + "   " +
                      outCenter.x      + " " + outCenter.y                 );
          ps.print("View_Checkboxes:  ");
          for(i=0; i<vCheckBox.length; i++) ps.print( vCheckBox[i].isSelected() ? 'Y' : 'N' );
          ps.println();
          ps.print("Toggle_Checkboxes:  ");
          for(i=0; i<tCheckBox.length; i++) ps.print( tCheckBox[i].isSelected() ? 'Y' : 'N' );
          ps.println();
          ps.print("Sticker_Colors:  ");
          for(i=0; i<54; i++) ps.print( fChar[ place[i].colorI ] );
          ps.println();
          for(i=0; i<6; i++) ps.println( bChar[i] + "   " + colToString(  faceColor[ bFace[i] ] ) +
                                                     "  " + colToString( labelColor[ bFace[i] ] )   );
          ps.println( "G   " + colToString(    bgColor )  + "  " + colToString(    fgColor ) );
          ps.println( "E   " + colToString( edge2Color )  + "  " + colToString( edge1Color ) );
          ps.close();
        } catch( IOException ee ) { System.err.println("Caught IOException: " + ee.getMessage() ); }
      }
    };

  String colToString( Color c ) { // Return a string representation of the 3 components.
    int i;
    float f[] = c.getRGBColorComponents(null);
    String s = "";
    for(i=0; i<3; i++) s = s.concat( Float.toString( f[i] ) + " " );
    return s;
  }

  class Filter extends javax.swing.filechooser.FileFilter { // Require .mc3 extension.
    public String getDescription() { return ".mc3 files"; }
    public boolean accept( File f ) {
      if( f.isDirectory() ) return true;
      String s = f.getName();
      int    i = s.lastIndexOf('.');
      return "mc3".equals( ( i>0  &&  i<s.length()-1 ) ? s.substring(i+1).toLowerCase() : null );
    }
  }

  String fix( File f ) {     // Add the ".mc3" extension to file path if not already present, and return path.
    String fn = f.getPath();
    return ( fn.length() < 4 || !".mc3".equals( fn.substring( fn.length() - 4 ) ) ) ? fn.concat(".mc3") : fn;
  }


  /* Recall State. */
  AbstractAction recall = new AbstractAction() {
      public void actionPerformed( ActionEvent ae ) {
        if( !startMethod.equals("application") ) return;
        JFileChooser fc = new JFileChooser(".");
        fc.setFileFilter( new Filter() );
        if( fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION ) return;
        if( !readStateFile( fix( fc.getSelectedFile() ) ) )
          JOptionPane.showMessageDialog( pane, "Sorry.  That failed for some reason.\n" +
                                         "(The name of the file on disk must end in \".mc3\".)",
                                         "State Recall Attempt", JOptionPane.WARNING_MESSAGE );
      }
    };

  /* Set up a disk file as the input stream for readState().  Return true if successful, false otherwise. */
  boolean readStateFile( String filePath ) {
    try {
      return readState( new BufferedReader( new FileReader( new File( filePath ) ) ) );
    } catch( FileNotFoundException e ) { return false; }
  }

  /* Restore a saved state from an input stream.  Return true if successful, false otherwise. */
  boolean readState( BufferedReader in ) {
    int i, j, k, nLines=0;
    int diff;                  // missing-parameter count for reverse compatibility when new items are introduced
    String   line;
    String[] s;
    float    f[] = new float[6];

    try {
      /* Read the lines of saved-state file and break them into tokens.  Line limit is arbitrary. */
      while( nLines<50  &&  ( ( line = in.readLine() ) != null ) ) {
        s = line.split(" +");
        if( s.length < 2 ) continue;

        /* Parse the tokens and modify state. */

        if( s[0].equals("Current_Parameter:") ) {
          curParam = Integer.parseInt( s[1] );
          param[ curParam ].setSelected( true ); // Value-display will occur when modifyParam() is called below.
        }

        /* Look for parameter-group IDs. */
        for(i=0; i<parButsSub.length; i++) {
          if( s[0].equals( parButsSub[i].mc3ID ) ) {
            diff = parButsSub[i].paramA.length - ( s.length - 1 ); 
            for(k=0, j=1; j < s.length  &&  k < parButsSub[i].paramA.length; k++) {
              Param p = parButsSub[i].paramA[k];
              if( p.skipOrder > 0  &&  p.skipOrder <= diff ) continue;  // Skip this one.
              modifyParam( p, Float.parseFloat( s[j++] ) );
            }
          }
        }

        /* Applets add an extra 20 pixels of height, which would reduce pane height if not compensated. */
        if( s[0].equals("Frame_Size:") ) {
          if( s.length < 3 ) continue;
          int height = Integer.parseInt( s[2] ) + ( startMethod.equals("application") ? 0 : 20 );
          frame.setSize( Integer.parseInt( s[1] ), height );
        }

        if( s[0].equals("Center_Locations:") ) {
          if( s.length < 4 ) continue;
          in_Center.set( Integer.parseInt( s[1] ), Integer.parseInt( s[2] ) );
          outCenter.set( Integer.parseInt( s[3] ), Integer.parseInt( s[4] ) );
        }

        if( s[0].equals("View_Checkboxes:") ) {
          diff = vCheckBox.length - s[1].length(); 
          for(i=0, j=0; i < vCheckBox.length  &&  j < s[1].length(); i++) {
            CheckBox cb = vCheckBox[i];
            if( cb.skipOrder > 0  &&  cb.skipOrder <= diff ) continue;  // Skip this one.
            cb.setSelected( s[1].charAt(j++) == 'Y' );
          }
        }

        if( s[0].equals("Toggle_Checkboxes:") ) {
          diff = tCheckBox.length - s[1].length(); 
          for(i=0, j=0; i < tCheckBox.length  &&  j < s[1].length(); i++) {
            CheckBox cb = tCheckBox[i];
            if( cb.skipOrder > 0  &&  cb.skipOrder <= diff ) continue;  // Skip this one.
            cb.setSelected( s[1].charAt(j++) == 'Y' );
          }
        }

        if( s[0].equals("Sticker_Colors:") ) {
          if( s[1].length() != 54 ) continue;
          for(i=0; i<54; i++) if( "LRFBDU".indexOf( s[1].charAt(i) ) < 0 ) break;
          if(i<54) continue;                           // Some nonsense in there.  Ignore line.
          for(i=0; i<54; i++) place[i].colorI = "LRFBDU".indexOf( s[1].charAt(i) );
        }

        /* Now looking for color-pair names. */
        for(i=0; i<8; i++) if( s[0].equals( "LRFBDUGE".substring(i,i+1) ) ) break;
        if(i==8) continue;                             // Did not recognize label on line.
        if( s.length < 7 ) continue;

        for(k=0; k<6; k++) f[k] = Float.parseFloat( s[k+1] );
        if(i==6) {                                     // Is a 'G'.  Background and foreground colors.
          bgColor    = new Color( f[0], f[1], f[2] );
          fgColor    = new Color( f[3], f[4], f[5] );
        } else if(i==7) {                              // Is a 'E'.  2D sticker edge color & 1D separator color.
          edge2Color = new Color( f[0], f[1], f[2] );
          edge1Color = new Color( f[3], f[4], f[5] );
        } else {                                       // Face color and label color.
          for(k=0; k<3; k++) colorComponents[i][k] = (double)f[k];
          labelColor[i] = new Color( f[3], f[4], f[5] );
        }
      }
      in.close();
    } catch( IOException e ) { System.err.println("Caught IOException: " + e.getMessage() ); return false; }

    createColors( faceColor,   1.f );
    createColors( dimColor1D, .85f );
    lastInFaceBright = 0.f;
    param[ curParam ].actionPerformed( dummyEvent );
    width = 0;                                         // Assure update at beginning of paintPane.
    cbChange.actionPerformed( new ActionEvent( this, 0, layeredBox.name ) );
    highlightsOff();
    frame.dispatchEvent( new ComponentEvent( frame, ComponentEvent.COMPONENT_RESIZED ) );
    stateRecover = true;
    paintPanes();
    return true;
  }


  /* A place to put test code. */
  boolean testOn = false;       // If true, a menu item for invoking test action below will show up.
  //boolean testOn = true;        // If true, a menu item for invoking test action below will show up.


  AbstractAction test = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) {
        System.out.println( (new Undo()).getClass().getName() );
  
      }
    };


/******** Help-Related Stuff, including all the strings. ********/

  /* Class HelpItem for describing and implementing items on Help menu. */
  class HelpItem extends JMenuItem implements ActionListener {
    String   name;
    String   content;

    HelpItem( String name, String content, int key ) {
      super( name );
      addActionListener(this);
      setMnemonic( key );
      this.name    = name;
      this.content = content;
    }

    public void actionPerformed( ActionEvent e ) {
      JOptionPane.showMessageDialog( null, content, name, JOptionPane.PLAIN_MESSAGE, null );
    }
  }

  /* Help strings */

  String     helpHelpS = new String (
    "The Help Menu consists of a number of specific help items, with emphasis on\n" +
    "briefly documenting the less obvious features of the program - like the\n" +
    "significance of the Shift, Control, and Alt modifier keys on the keyboard.  As\n" +
    "it turns out, there are plenty of nonobvious features.\n\n" +
    "The ConfigSelect Menu allows you to choose among some different configurations\n" +
    "for the program.  These are worth checking out to get familiar with what the\n" +
    "program can do. \n\n" +
    "The check boxes on the View Menu control what aspects of the display appear or\n" +
    "not, while those on the Toggles Menu enable or disable certain behaviours of the\n" +
    "program.\n\n" +
    "The function of most of the various menu items should be obvious.  When not, it\n" +
    "should usually be easy to learn by means of experimentation.  If that fails, it\n" +
    "probably does not matter.\n\n" +
    "In the help for mouse-based operations, the buttons are referred to as button 1\n" +
    "and button 2.  However, what is called \"button 2\" may actually be any button\n" +
    "other than 1.  Thus the buttons can usually be the left and right buttons\n" +
    "respectively.  If no button number is mentioned, any button can be used.\n\n" +
    "For detailed documentation read the pages on the Web site.\n\n" +
    "The performance of the program depends almost exclusively on how large you make\n" +
    "the image.");

  String    twistHelpS = new String (
    "Click anywhere in 2D display.  Which button determines direction.  If click is\n" +
    "on a sticker, corresponding slice is turned.  A click that misses all stickers\n" +
    "is resolved to closest face center, except that around the outside clicks only\n" +
    "refer to in-facing faces.\n\n" +
    "Click on a BFUDLR character under either tattle.\n\n" +
    "Type a BFUDLR character.\n\n" +
    "Use Shift modifier to reverse direction.\n" +
    "Use Control modifier to turn a center slice.\n" +
    "Use Alt modifier to turn whole cube.\n" +
    "Use Alt and Control modifiers together to turn the external slices.\n\n" +
    "For mouse, last method above works only for clicks NOT ON stickers; for\n" +
    "otherwise mouse-down is treated as the beginning of a drag for center moving.\n\n" +
    "A click in either 1D projection area initiates a twist of the face whose face\n" +
    "sticker is closest in the projection.  Unless you disable the Drive Mode feature\n" +
    "(Toggles Menu), only button 1 is available for twisting in the rover area (as\n" +
    "button 2 is for Drive Mode), but this is intended primarily for the purpose of\n" +
    "taking over the animation with the mouse, in which case the direction does not\n" +
    "matter.  (If you want a clockwise turn, you can still click on the appropriate\n" +
    "BFUDLR character under one of the tattles.)\n\n" +
    "If you command twists at a rate faster than the animation can perform them, the\n" +
    "not-yet-done twists are queued and performed in FIFO order.  In particular, a\n" +
    "180 degree twist can be commanded with a double click for which the second click\n" +
    "occurs while animation for the first twist is still in progress.\n\n" +
    "If you precede any twist command (or Scramble) with the Mirror Prefix command \n" +
    "(Control-Q), mirroring transformations will be performed.  If you click on a \n" +
    "sticker, that sticker will be stationary in the reflection process.");

  String     undoHelpS = new String (
    "The results of twists and drags, modification of the 3D viewing\n" +
    "transformation, modification of rover parameters, scrambling, and\n" +
    "initializing can be undone and redone.  The stacks for both undoing\n" +
    "and redoing hold 100 operations each.\n\n" +
    "If you use the mouse-control-of-animation feature to produce a 180\n" +
    "degree twist, it will be treated as two 90 degree twists for the \n" +
    "purpose of undoing and redoing.\n\n" +
    "Undone operations remain on the redo stack even when new operations are\n" +
    "performed.  Thus if you forgot something 2 steps back, you can go\n" +
    "undo, undo, what-you-missed, redo, redo.  This does not apply exactly\n" +
    "for anything but twists, as redoing a non-twist puts state back to \n" +
    "the state that existed before the undo of a previous operation, as\n" +
    "opposed to performing some operation relative to the current state.\n\n" +
    "The Cancel Undo command is like the Redo command except that whatever\n" +
    "it (re)does is not itself undoable.  (Useful for removing from the\n" +
    "undo/redo stacks actions like reorientation or center moving that you\n" +
    "do not really want undone.)\n\n" +
    "Changes to Rover parameters or the 3D viewing transformation are\n" +
    "optionally undoable; and there are checkboxes on the Toggles Menu\n" +
    "which you can use to control this.  By default, view changes are\n" +
    "undoable and rover changes are not.\n\n" +
    "If you do Undo or Redo commands at a rate faster than the animation \n" +
    "can execute them, the pending commands are queued and performed in \n" +
    "FIFO order.");

  String    paramHelpS = new String (
    "The Parameters Menu consists of a set of radio buttons.\n" +
    "Which one you select determines which parameter is displayed and is \n" +
    "modifiable via the slider and numeric display field at the bottom.\n\n" +
    "The four parameters in the first group control the viewing trans-\n" +
    "formation for the 2D display.  Except for the viewing distance, \n" +
    "these parameters can also be changed by dragging with the mouse.\n" +
    "Similarly the third group for roving eye state can also be modified\n" +
    "through graphical interaction.  In cases where a parameter is being\n" +
    "modified by means other than the numeric input field or the slider,\n" +
    "you can continuously monitor the effect of the interactive modifi-\n" +
    "cation on the displayed numeric value of the selected parameter.\n\n" +
    "There is a help item, Appearance Parameters, which offers some \n" +
    "explanations for the second to last group of parameters.\n\n" +
    "Use of the Scale Limit is a bit tricky and that also has its own\n" +
    "help item.");

  String   appearHelpS = new String (
    "The second to last group of parameters on the Parameters Menu affect the way the\n" +
    "program behaves while drawing stickers.  Considerations are mostly aesthetic.\n\n" +
    "Sticker-Edge Line-Width controls how heavy a black line the program draws for\n" +
    "the boundary of a sticker.  These lines actually obscure the polygons for the\n" +
    "stickers themselves somewhat.  This effect is most obvious when a face of the\n" +
    "puzzle is seen nearly edge-on.  Drawing the edges is not particularly important\n" +
    "when sticker size is any significant amount less than 1.  Some may consider it\n" +
    "to matter when the drawing of an out-facing sticker overlaps that of an\n" +
    "in-facing one, as it displays the edge explicitly.  However, lack of the edge is\n" +
    "really only a problem if the stickers are the same color, and, even then, a\n" +
    "fairly minor one.  A setting of this parameter which is less than 0.4 turns the\n" +
    "edge lines off completely.  (They look ugly if the program permits any smaller\n" +
    "value.)  Configuration #5 shows an example of using a very heavy line for\n" +
    "sticker edges.\n\n" +
    "The separator width for stickers in the 1D projections is similar; but more \n" +
    "important.  They are turned off for values below 0.1.  (They still look \n" +
    "better with it off, but this does create ambiguities for same-colored stickers.)\n\n" +
    "Brightness of In-Faces can be used to reduce the apparent brightness of faces\n" +
    "which are seen from the inside.  An example of a situation in which this is\n" +
    "useful occurs when sticker edges are not being drawn at all, as it will still\n" +
    "provide some contrast for an out-facing sticker which overlaps an in-facing one\n" +
    "of the same color.  Configuration #7 is an example in which the edges are not\n" +
    "being drawn and the in-facing stickers are dimmed somewhat.\n\n" +
    "Out-Face Alpha controls transparency of outward facing stickers.  This is\n" +
    "illustrated in Configuration #6.  This feature turns out to be not very useful,\n" +
    "but it makes some interesting looking pictures.  (Try scrambling and tumbling.)");

  String    scaleHelpS = new String (
    "After projecting the 3D model of the puzzle onto the projection plane, the\n" +
    "coordinates still need to be scaled for graphic presentation.  The program\n" +
    "maintains a scale value by which it multiplies coordinate values in MCS units to\n" +
    "obtain coordinates in pixels.  (Roughly speaking, the length of the edge of a\n" +
    "cubie is 1 in MCS units, but this can be distorted by the perspective\n" +
    "transformation.)  The scale value used by the program will never exceed the\n" +
    "Scale Limit parameter you specify.\n\n" +
    "The program always computes a maximum scale, max-scale, which, depending on\n" +
    "current window size and center positions assures no sticker will plot outside\n" +
    "the provided drawing area.  The auto-Scaling feature refers to automatic update\n" +
    "of the Scale Limit.  When it is enabled and you change the window size or modify\n" +
    "a parameter that can affect max-Scale, the Scale Limit parameter is updated to\n" +
    "the max-scale value.  The idea is that the automatic setting makes the picture\n" +
    "as big as possible.  Resetting of the Scale Limit will also occur when you\n" +
    "toggle the auto-Scale option to an enabled state.  As long as your Scale Limit\n" +
    "is less than the program's max-scale, it IS the scale.\n\n" +
    "If you wish to observe the effect of changing things like the face and sticker\n" +
    "sizes or the 3D viewing distance, it is preferable to think of that in terms of\n" +
    "model coordinates which are not being rescaled dynamically as you change the\n" +
    "parameters.  To assure this, you can reduce the Scale Limit parameter or disable\n" +
    "auto-Scaling and make the window bigger.");

  String     dragHelpS = new String (
    "There are five reasons for dragging: to MOVE A CENTER, to CONTROL\n" +
    "THE ANIMATION, to ROTATE the 2D image, to TUMBLE the 3D effect,\n" +
    "and to make adjustments to ROVER parameters.  (\"Tumbling\" refers \n" +
    "to moving the eyepoint around on the sphere whose radius is \n" +
    "the 3D viewing distance so you observe cube from arbitrary points of\n" +
    "view.)  There are separate help items to tell you how to get into \n" +
    "and use each corresponding state.\n\n" +
    "Once you have initiated a drag by any means, the number of and which\n" +
    "buttons you are holding down ceases to make any difference.\n" +
    "The drag ends when the last button comes up.\n\n" +
    "To enable single-button dragging for rotating and tumbling,\n" +
    "reduce the Drag Threshold parameter to a small number like 4.0." );

  String     moveHelpS = new String (
    "With Control and Alt both depressed, press a mouse button down with the\n"+
    "cursor over a sticker associated with the center you wish to move.\n\n"+
    "You can release the modifier keys once the drag is started." );

  String  animateHelpS = new String (
    "Depress a mouse button while timer-based animation is in progress and\n" +
    "start moving the mouse immediately.  While the mouse button remains\n" +
    "down, the angle of the twist will depend on mouse cursor position.  If\n" +
    "there is only one face which is out-facing and you do a twist about\n" +
    "that face's axis, the angle for the animation depends on angular\n" +
    "position of the mouse cursor relative to the center.  Otherwise and\n" +
    "for other twist axes, the angle depends on cursor motion perpendicular\n" +
    "to the twist axis.\n\n" +
    "You can also depress a mouse button in the rover 1D projection area,\n" +
    "in which case only horizontal motion of the mouse affects the current\n" +
    "twist angle.  (Note that you can also initiate a twist by clicking\n" +
    "with button 1 in the rover 1D projection area.  This is intended\n" +
    "primarily for taking over control of the resulting twist animation\n" +
    "with the mouse.)\n\n" +
    "It is possible to enter twist commands faster than than they can be\n" +
    "animated, in which case there can be pending twists in addition to\n" +
    "that of the animation in progress.  In such case, you cannot take over\n" +
    "the animation with the mouse - at least not until the last pending\n" +
    "twist starts.  \n\n" +
    "If you try to command a new twist by clicking with the mouse while a\n" +
    "twist animation is in progress, it is important that the click be\n" +
    "perceived as a click and not as drag, or else you will be taking over\n" +
    "the animation.  Once there is a pending twist besides the one in\n" +
    "progress, this ceases to be a problem, since taking over the animation\n" +
    "is no longer permitted.");

  String   tumbleHelpS = new String (
    "Drag radially from near one of the two virtual puzzle centers with two\n" +
    "mouse buttons depressed.\n\n" +
    "Tumbling is achieved by moving the eyepoint around on the sphere\n" +
    "centered on the origin with a radius equal to the 3D viewing distance.\n" +
    "It appears that the cube is rotating around an axis which lies in the\n" +
    "projection plane.  That axis is perpendicular to the line segment from\n" +
    "where your drag started to the current mouse position.  The amount of \n" +
    "of turn is proportional to the length of that line.\n\n" +
    "To view the orientation of the axis for the tumble, you can enable\n" +
    "display of a representation of it via the View Menu.  The program \n" +
    "always draws a little \"+\" where the drag started.  It does not \n" +
    "correspond to the cube center which is indicated on the axis line.");

  String   rotateHelpS = new String (
    "Drag circumferentially from periphery with two mouse buttons depressed.\n" +
    "Rotation angle depends on the angular position of cursor relative to the\n" +
    "center.\n" );

  String  dragRovHelpS = new String (
    "The parameters for the roving eye can be adjusted by dragging in the rover 1D\n" +
    "projection area at the bottom of the display.  You can use a single button (any)\n" +
    "and indicate the type of drag with modifier keys, or you can use a two-button\n" +
    "method to indicate the drag type without using modifier keys.  Initiating the\n" +
    "drag with the two-button method requires first pressing two buttons and then\n" +
    "releasing one.  Furthermore, which button you press first matters:\n\n" +
    "Sideways Slide:                1 click 2    or   Control-drag\n" +
    "    If button 1 remains down and you started with button 1, the drag is for\n" +
    "    sliding the eyepoint sideways relative to the rover heading.\n\n" +
    "Forwards/Backwards:      Roll 2 to 1   or   Control-Shift-drag\n" +
    "    If button 1 remains down and you started with button 2, the drag is for\n" +
    "    forwards and backwards movement along the direction of the rover heading.\n\n" +
    "Heading Adjustment:         2 click 1    or   Alt-drag\n" +
    "    If button 2 remains down and you started with button 2, the drag is for\n" +
    "    adjusting the rover heading.\n\n" +
    "View Angle Adjustment:    Roll 1 to 2   or   Alt-Shift-drag\n" +
    "    If button 2 remains down and you started with button 1, the drag is for\n" +
    "    adjusting the view angle.\n\n" +
    "Only horizontal movement of the mouse matters in the rover's 1D projection area.\n" +
    "The intent for the directional sense in the sideways slide and heading\n" +
    "adjustments is that it 'feel like' you are dragging the position of the image on\n" +
    "the screen, as with tumbling the 2D image.  But the calibration of sensitivity\n" +
    "for this movement for heading adjustment varies with the distance from what you\n" +
    "are looking at and for eye movement with the view angle.  For the other two,\n" +
    "dragging to the right will enlarge the image, while dragging to the left will\n" +
    "shrink it.");

  String    roveGenHelpS = new String (
    "The roving eye allows the perspective view generated in the Rover 1D projection area\n" +
    "to be taken from any point of view in the 2D drawing area.  The roving eye has four\n" +
    "parameters which determine its state.  Two determine its plot position.  In the GUI,\n" +
    "they are fractional positions relative to the width and height of the window.  The\n" +
    "other two determine the rover's Heading and its View Angle.  Adjusting these second\n" +
    "two parameters is akin to adjusting the pan and zoom for the view.  \"Heading\" makes\n" +
    "more sense in this context than \"pan\", because it also determines what direction is\n" +
    "\"forwards\" when moving the rover by interactive means.  For the zoom, the most\n" +
    "convenient units for specifying it would seem to be the view angle, in which case\n" +
    "\"View Angle\" seems a more appropriate term for the parameter.  What is drawn to\n" +
    "indicate the rover's position is just the boundary of its field of view as determined\n" +
    "by heading and view angle.\n\n" +
    "Adjustment of rover parameters via the slider or numeric field is not so convenient\n" +
    "(though the effect of interactive adjustment can be monitored with them).  The\n" +
    "program provides a number of distinct alternative ways in which to modify them.  For\n" +
    "moving the rover, the modes of motion are a sideways slide (left and right) or\n" +
    "forwards and backwards motion along its heading direction.\n\n" +
    "One method for adjusting heading and position is called \"Drive Mode\".  It is akin to\n" +
    "driving a vehicle.  See the separate help item for it.\n\n" +
    "The remaining rover adjustment methods involve dragging in the Rover 1D projection\n" +
    "area or use of arrow keys (either group) with the Control and Alt modifiers.  When\n" +
    "you use the arrow keys, continuous change of a parameter is initiated and proceeds on\n" +
    "a timed basis.  Similar adjustments can also be made by dragging with the mouse.  In\n" +
    "general, the Control modifier is associated with position-moving operations, while\n" +
    "the Alt modifier is associated with heading and view angle changes.  If you think of\n" +
    "the Control and Alt keys as being oriented left and right, there is also a related\n" +
    "left/right association that can be made with the two-button methods for initiating\n" +
    "the drag based on which button remains down.");

  String    roveArrowHelpS = new String (
    "To move the roving eye use arrow keys with Control modifier.\n" +
    "To change heading or view angle use arrow keys with Alt modifier.\n" +
    "Speed triples with each repeat of corresponding arrow key.  It stops \n" +
    "when you hit any other arrow key, after which you can restart.\n\n" +
    "It is possible to get the view field indicator stuck in a corner\n" +
    "so that you can no longer see where it is.  If this happens to you,\n" +
    "you can use Alt-left several times to get the heading swinging \n" +
    "around rapidly so the view field indicator will become visible again.\n\n" +
    "Adjusting the rover by dragging the mouse is compatible with \n" +
    "simultaneous adjustment by the arrow keys.  Some interesting\n" +
    "possibilities exist.");

  String   driveHelpS = new String (
    "In addition to dragging with the mouse, there is another method for controlling\n" +
    "the position and heading of the rover with the mouse.  This method is called\n" +
    "\"Drive Mode\", as it is more nearly analogous to driving a vehicle.\n\n" +
    "Mouse button 1 is the brake.  Button 2 or button 1 with the Shift modifier\n" +
    "depressed is the accelerator.  To start, click the accelerator when the mouse\n" +
    "cursor is in the rover 1D projection area.  The speed of the rover doubles each\n" +
    "time you click the accelerator.\n\n" +
    "Steering is controlled by the position of the mouse cursor relative to the\n" +
    "horizontal center of the display.  (There is a little mark there on the\n" +
    "horizontal line which marks the top of the rover 1D projection area.)  Think of\n" +
    "the cursor as being attached to the top of a steering wheel (or a flight control\n" +
    "stick).\n\n" +
    "Drive Mode is enabled by default, but there is a check box on the Toggles Menu\n" +
    "which can be used to disable the feature.  It can be nuisance if you try to use\n" +
    "a button 2 click in the rover projection area to start a twist.  (If you tend to\n" +
    "make this mistake, you might want to select the check box which makes rover\n" +
    "changes undoable rather than disable the Drive Mode feature.)\n\n" +
    "The rover will stop moving if hits a boundary of the 2D area.  It will also stop\n" +
    "if you move the mouse cursor out of the rover projection area.\n\n" +
    "There is no reverse direction mode for driving the rover.  However, if you do\n" +
    "get it stuck up against a boundary, you can still use one of the\n" +
    "forwards/backwards adjustment methods to back off.  You can also use heading\n" +
    "adjustment to get the rover pointed back inwards\n\n" +
    "While driving the rover, you can still use keyboard commands to change the Rover\n" +
    "View Angle.  (Actually, you can do almost anything with the keyboard while the\n" +
    "rover is moving - e.g., scramble, twist.)");

  String  saveRecallHelpS = new String (
    "The current state of the program can be saved using the \"Save State\" command.\n" +
    "A saved state includes the settings of all the parameters, the states of the\n" +
    "checkboxes on the View and Toggles menus, the colors, the size of the frame, the\n" +
    "positions of the centers, and the current permutation of the stickers.\n\n" +
    "Saved states are represented by disk files with extension .mc3.  By default they\n" +
    "are expected in and placed in the directory in which the executable for the\n" +
    "program resides.  Any such saved state can be recovered using the Recall State\n" +
    "command.  When specifying a file name, you do not need to specify the .mc3\n" +
    "extension.  If your file name lacks such an extension, one will be appended.\n\n" +
    "If the directory from which the program executes contains a saved state file\n" +
    "with name \"default.mc3\", then that state will be set when the program first\n" +
    "starts.  After you have found a configuration for the program which you like,\n" +
    "you will probably want to save that as your default.  For your default, you\n" +
    "probably do not want to save state when the cube is in a scrambled state.  You\n" +
    "can also specify a file argument on the command line which will supercede\n" +
    "\"default.mc3\" as the state-file for the initial settings.  (\".mc3\" still\n" +
    "optional on command line argument, but the filename itself must end in\n" +
    "\".mc3\".)");

  String saveFileHelpS = new String (
    "The .mc3 state-saving files are plain text.  Each line in a save file is\n" +
    "identified by a token starting in the first column.  You can omit lines from a\n" +
    "save file if you do not want the corresponding aspects of state to be recalled.\n" +
    "Except for the colors (See below.), it is not recommended that you edit the\n" +
    "values in a save file, as the program does not take any significant measures to\n" +
    "protect itself from bad data.\n\n" +
    "During a recall, the program ignores lines for which it does not recognize the\n" +
    "token starting in the first character position.  It also ignores lines which\n" +
    "make no sense for other reasons - like not having enough parameters.  The order\n" +
    "of the lines in a save file does not matter.\n\n" +
    "The colors are written in the last 8 lines of the file in a simple format\n" +
    "specfying fractional RGB components.  On these lines, the identifying token is a\n" +
    "single character which determines which colors are being specified.  If it is a\n" +
    "BFUDLR character, the first three decimal numbers are for the corresponding\n" +
    "sticker color; and the second group of three numbers are for the color used for\n" +
    "a character (most often the one identifying the line) which is drawn as a label\n" +
    "on top of a sticker with the associated sticker color.  If the first character\n" +
    "is a 'G' the two colors are the background and foreground colors respectively.\n" +
    "If the first character is a 'E' the colors are for 2D sticker edges and 1D \n" +
    "separators.  Knowing this, you can also specify new colors by editing a save \n" +
    "file if this is more convenient.  (Some folks may already have their favorite \n" +
    "set of colors in RGB format.)");

  String  colorsHelpS = new String (
    "The default colors for the program follow the most obvious\n" +
    "conceivable pattern.  R, G, and B are matched up with the positive\n" +
    "x, y, and z directions, while C, M, and Y are matched with the\n" +
    "negative directions.  The RGB and CMY triples of colors should be\n" +
    "familiar to most folks.  (In case \"CMY\" is not, those letters\n" +
    "stand for cyan, magenta, and yellow - the colors complementary to\n" +
    "red, green, and blue, respectively.  The CMY colors are appropriate\n" +
    "for mixing pigments, while the RGB colors are best suited to the\n" +
    "mixing of light.)\n\n" +
    "Logical though they may be, some may regard the default colors as\n" +
    "unfamiliar or aesthetically unpleasing.  (There is a slight problem\n" +
    "in that the human eye does not distinguish blue and cyan all that\n" +
    "well, cyan appearing somewhat like an unsaturated blue.  However, as\n" +
    "can be seen by examining a save file, the default colors have been\n" +
    "tweaked slightly to improve discrimination.)  The program allows you\n" +
    "to specify any colors you like using the Adjust Color command.  This\n" +
    "is hardly worth the trouble with the Applet version of the program.\n" +
    "However, the colors are saved with a saved state in the application\n" +
    "version; so, if you are running the application, you can save your\n" +
    "color modifying efforts.\n\n" +
    "If you change sticker colors, the defaults for face labels may\n" +
    "become inappropriate, so you can also adjust the label colors.  You\n" +
    "may also change the background and foreground colors and the colors\n" +
    "used to draw sticker edges/separators in the 2D/1D areas.");

  String  labelsHelpS = new String (
    "In the 2D display, face labels are the characters which (by default) are shown on\n" +
    "each face sticker.  They are intended to identify the faces to which those face\n" +
    "stickers belong.  There is a checkbox on the View Menu which can be used to turn\n" +
    "these indicators off.  By default the labels are upper case and specify the name of\n" +
    "the color of the face sticker on which they are plotted.  This also happens to be\n" +
    "the name of the associated face and the associated slice.  Thus this method\n" +
    "corresponds to the Face Coordinate System.\n\n" +
    "There is a checkbox on the Toggles Menu which can be used to switch the labels so\n" +
    "that they indicate MCS directions, in which case they appear as lower case since\n" +
    "they specify direction vectors as opposed to names of things.  In the MCS case, the\n" +
    "indicated direction is the MCS direction in which the face currently faces.  Note\n" +
    "that, if such a direction indicator is on a face sticker in a center slice which is\n" +
    "turning during animation, the labels are not displayed, as the faces are not facing\n" +
    "along any particular axis until the animation ends.  There is no equivalent problem\n" +
    "for the FCS labels as the FCS turns with a turning center slice.\n\n" +
    "There is no particular significance to the color of a label in the 2D display or in\n" +
    "the Colored Tiles display of the Layered-View Window.  They are written in the label\n" +
    "color associated with the color of the stickers on which they appear.  By default,\n" +
    "that is the complementary color for contrast's sake.  It is not intended that\n" +
    "significance be attached to the color of the label in these cases.\n\n" +
    "When face labels are enabled, labels also appear in the 1D projection areas.  These\n" +
    "are always FCS labels (face names) and they are displayed in the colors associated\n" +
    "with the faces they identify.  The Colored Tiles display in the Layered-View Window\n" +
    "always uses FCS labels, as the MCS orientation of that display never changes and is\n" +
    "easy to remember.");

  String  layeredHelpS = new String (
    "If you select the Layered-View Window checkbox on the View Menu, the\n" +
    "program will produce a different kind of representation of the state\n" +
    "of the cube.  It may be thought of as layered or flat.  The\n" +
    "presentation in the main window is focussed on stickers.  That in the\n" +
    "layered presentation is focussed on cubies.  There are two different\n" +
    "presentations.  The one on the left is referred to as the \"Colored\n" +
    "Tiles\" presentation, while that on the right is referred to as the\n" +
    "\"Text Display\".  They both present the same information.  Each little\n" +
    "square corresponds to a cubie.  Each 3x3 'board' corresponds to the\n" +
    "cubies in one of the 3 slices perpendicular to the z-axis.  The\n" +
    "U-slice is at the top.\n\n" +
    "You can do twists by clicking in the layered view window.  Clicks are\n" +
    "resolved to the nearest face cubie.  The turning in the layered window\n" +
    "is always in terms of the 3D turning direction.  I.e., defined as if\n" +
    "you were looking face-on at the turned face from the outside of the\n" +
    "cube.  This is most surprising for the D face.\n\n" +
    "All keyboard commands (pure ones, as opposed to those that work via\n" +
    "the menus) are valid in the Layered-View Window.  So, BFUDLR\n" +
    "characters command twists, and the control keys work for the commands\n" +
    "on the Commands Menu.");

  String  tiledHelpS = new String (
    "In the Colored Tiles presentation, the colored areas of each square\n" +
    "are arranged to indicate in which directions the corresponding cubie's\n" +
    "stickers are facing.  For a corner cubie, the color for a sticker\n" +
    "which faces up or down is shown in the small square near the tile for\n" +
    "the face cubie in the slice.  For up or down facing stickers on edge\n" +
    "cubies in the U or D slice, the color is shown on the inside half of\n" +
    "the tile.  For all other stickers, the corresponding color will extend\n" +
    "to the edge of the tile which faces in the corresponding direction.\n" +
    "No 3D interpretation of a tile's surface is intended.\n\n" +
    "Compare to Configuration #2.  The three rings of laterally facing (not\n" +
    "up or down) stickers surrounding the D face match up with the outsides\n" +
    "of the 3 3x3 arrays of cubie squares.  Try clicking on the U and D\n" +
    "faces, with and without the Control modifier.");

  String  textHelpS = new String (
    "In the Text Display, each cubie has a name in upper case,\n" +
    "corresponding to the names of the colors of its stickers.  The\n" +
    "relative position in which the sticker color name is displayed\n" +
    "corresponds to the axis (xyz order) which is perpendicular to sticker\n" +
    "after initialization.  Blank columns correspond to axes for which the\n" +
    "coordinate value of the cubie's location is zero - i.e., no sticker\n" +
    "for that axis.\n\n" +
    "Under each sticker name is a lower case letter which indicates in\n" +
    "which direction the corresponding sticker currently faces.  These\n" +
    "lower case characters are called \"Direction Indicators\".  By default\n" +
    "they use the Face Coordinate System; so reorienting the whole cube\n" +
    "does not change them.  However, if you do reorient the cube, you have\n" +
    "to interpret them relative to the current orientation of the faces.\n" +
    "There is a toggle which will switch the Direction Indicators to use\n" +
    "the MCS.  As long as you leave the cube in its default orientation,\n" +
    "there is no difference.\n\n" +
    "In the text display, all the information is in the text.  The color\n" +
    "coding of the character backgrounds is just a bonus.  However, what is\n" +
    "relevant, color-wise, is the color of the background, not the color of\n" +
    "the label which appears there.  Think of the background for the\n" +
    "character as the sticker.  (With the default color scheme, the color\n" +
    "of a label for a sticker is the color of the opposite face.  This is\n" +
    "just to achieve good contrast.  Beware of attaching any directional\n" +
    "significance to the color of the label itself.");

  String  centersHelpS = new String (
    "The program draws nothing but stickers.  The side of a sticker which\n" +
    "is visible from the eyepoint may be either that which faces outwards\n" +
    "from the center of the puzzle or that which faces inwards.  Stickers\n" +
    "are called in- or out-facing depending on which side you are looking\n" +
    "at.  Because the out-facing stickers can obscure your view of\n" +
    "in-facing ones, the program offers the option of separating the two\n" +
    "groups of stickers.  This is done by maintaining two virtual cube\n" +
    "centers, the in-center and the out-center, relative to which are \n" +
    "plotted the corresponding stickers.\n\n" +
    "The auto-Position toggle controls whether or not the program will\n" +
    "automatically decide where to put the centers when you change the\n" +
    "window size or enable auto-Position.  If the window size is such that\n" +
    "the 2D drawing area is roughly square, the centers will be made to\n" +
    "coincide, in which case you can get some fairly normal looking\n" +
    "cube simulators.  If the area is oblong, the program will separate\n" +
    "the centers so that no out-facing stickers plot atop in-facing ones.\n" +
    "The displacement can be vertical or horizontal depending on the \n" +
    "aspect ratio of the window.");

  String   coordsHelpS = new String (
    "The model of the cube exists in a coordinate system called the Model Coodinate\n" +
    "System, which is abbreviated to MCS.  The faces of the cube and colors of the\n" +
    "stickers are named for the MCS directions in which they face after initialization.\n" +
    "The directions are abbreviated with the bfudlr characters which stand for Back (+y),\n" +
    "Front (-y), Up (+z), Down (-z), Left (-x), and Right (+x).  Upper case versions of\n" +
    "these characters are used to name the colors, the faces, and the slices depending on\n" +
    "how they are oriented on initialization.  The program does not require that the R\n" +
    "face, say, always face in the r direction.  I.e., you can reorient the cube within\n" +
    "the MCS.  This happens whenever you do a turn of a center slice or when you turn the\n" +
    "whole cube.  The color coding of a bfudlr character under the MCS tattle in the\n" +
    "lower left indicates the color of the face sticker which is currently facing in the\n" +
    "corresponding MCS direction.\n\n" +
    "In the MCS, the model of the cube is centered at the origin.  The cubies have\n" +
    "edges of length 1.  Thus the coordinates of the centers of the cubies must be\n" +
    "one of -1, 0, or 1.  The faces of the complete cube are at distance 1.5 from the\n" +
    "origin.\n\n" +
    "There is another coordinate system which is probably preferable for theoretical\n" +
    "purposes.  It is called the Face Coordinate System, which is abbreviated to FCS.\n" +
    "This coordinate system is determined by the current orientation of the cube and\n" +
    "it turns with the cube whenever you reorient in the MCS.  E.g., the x-direction\n" +
    "is always that in which currently faces the R face of the cube.  After\n" +
    "initialization, the MCS and FCS are the same.\n\n" +
    "By default, the coordinate system for typing BFUDLR characters on the keyboard\n" +
    "is the FCS, but there is a toggle to switch it to the MCS.");

  String   tattleHelpS = new String (
    "Since the eyepoint for viewing the cube can be placed anywhere, the relationship of\n" +
    "the MCS to the display is arbitrary.  In order to to let you know (if you care) how\n" +
    "the MCS is being projected to the display, the program can draw a little tattle in\n" +
    "the lower left corner which illustrates the orientation of the images of the basis\n" +
    "vectors for the MCS.  Their endpoints are labelled as x, y, or z.  The xyz\n" +
    "characters are color coded to indicate which face currently faces in the\n" +
    "corresponding MCS direction.  These colors change if you reorient the cube in the\n" +
    "MCS.  The bfudlr characters below the tattle are similarly color coded and change\n" +
    "correspondingly.  The color of one of those bfudlr characters is the color of the\n" +
    "face sticker which currently faces in the MCS direction specified by the character.\n\n" +
    "The tattle in the lower right is for the FCS.  For this tattle, the colors of x, y, z\n" +
    "always remain those associated with the face stickers on the corresponding faces as\n" +
    "the coordinate axes of the FCS turn with the faces.  Similarly, the color coding on\n" +
    "the BFUDLR characters for the FCS never changes.\n\n" +
    "There is an extra bit of color coding which goes on with the legs of the tattles to\n" +
    "help you tell how they are oriented.  If the endpoints of all three legs of a tattle\n" +
    "are closer to the eyepoint than is the apex where they meet, then they will all be\n" +
    "drawn in white.  However, if any points away, then the one which points farthest\n" +
    "away remains white, but the other two legs are coded with the color of the face\n" +
    "sticker pointing the direction opposite the white leg - corresponding to the color\n" +
    "of the face sticker on the face the edges of which are parallel to these two vectors.");

  String  projectHelpS = new String (
    "The program uses perspective projection to generate the 2D image.  The\n"+
    "projection plane passes through the origin and is perpendicular to the\n"+
    "line from the origin to the eyepoint.  A given point is projected by\n"+
    "casting a ray from the eyepoint through the given point.  Where that\n"+
    "ray intersects the projection plane is the projection.  The result is\n"+
    "that the size of things changes depending on how far they are from the\n"+
    "eyepoint.  More specifically, depending on the component of the vector\n"+
    "from the point to the eyepoint in the direction normal to the\n"+
    "projection plane.  The amount of the size-scaling is the ratio of the\n"+
    "viewing distance to that component of the displacement of the\n"+
    "projected thing from the eyepoint.  Thus things that are on the far\n"+
    "side of the projection plane from the eyepoint get smaller, and things\n"+
    "on the near side get bigger in the projection.  (At any time, the\n"+
    "projection plane cuts the cube in half, so the growing and shrinking\n"+
    "are 'balanced'.)  Stickers which are not parallel to the projection\n"+
    "plane are thus distorted, with their far edges appearing shorter than\n"+
    "their near edges." );

  String eyepointHelpS = new String (
    "The position of the eyepoint in the MCS is determined by its elevation\n"+
    "and azimuth.  An azimuth of zero corresponds to 'north' or positive\n"+
    "y-axis.  Elevation is with respect to the xy-plane.  Thus an azimuth\n"+
    "of 0. and an elevation of 0. puts the eyepoint on the positive y-axis.\n"+
    "Many of the builtin configurations use an elevation of 90 degrees to\n"+
    "place the eyepoint on the z-axis.  In general the eyepoint can be\n"+
    "moved to any position on a sphere whose radius is the 3D viewing\n"+
    "distance.  The minimum allowed value for the 3D viewing distance\n"+
    "assures that that sphere still contains the entire cube.  I.e., the\n"+
    "eyepoint is constrained from 'hitting' the cube.  At the minimum\n"+
    "viewing distance the distortion from the perspective projection is\n"+
    "extreme.  At the maximum viewing distance, the appearance hardly\n"+
    "differs from an orthogonal projection.\n\n"+
    "Though the elevation and azimuth of the eyepoint can be changed\n"+
    "directly as parameters, the way that works is not very intuitive.  The\n"+
    "tumbling method, for which it 'feels like' you are turning the cube\n"+
    "itself is probably preferable.  (You can monitor its effect on\n"+
    "elevation or azimuth by selecting the desired one on the Parameters\n"+
    "Menu.)" );

  String    aboutHelpS = new String (
    "<html><center><FONT SIZE=\"+1\">Magic Cube 3D<br>"+
    "Version 1.06 - July 1, 2006<br>"+
    "by David Vanderschel</center></html>" );

  HelpItem        helpHelp  = new HelpItem( "Help Help",                     helpHelpS, KeyEvent.VK_H );
  HelpItem       paramHelp  = new HelpItem( "Parameters General",           paramHelpS, KeyEvent.VK_P );
  HelpItem      appearHelp  = new HelpItem( "Appearance Parameters",       appearHelpS, KeyEvent.VK_A );
  HelpItem       scaleHelp  = new HelpItem( "Scaling",                      scaleHelpS, KeyEvent.VK_S );
  HelpItem        dragHelp  = new HelpItem( "Dragging General",              dragHelpS, KeyEvent.VK_D );
  HelpItem        moveHelp  = new HelpItem( "Move a Center",                 moveHelpS, KeyEvent.VK_M );
  HelpItem     animateHelp  = new HelpItem( "Control Animation",          animateHelpS, KeyEvent.VK_A );
  HelpItem      tumbleHelp  = new HelpItem( "Tumble",                      tumbleHelpS, KeyEvent.VK_T );
  HelpItem      rotateHelp  = new HelpItem( "Rotate",                      rotateHelpS, KeyEvent.VK_R );
  HelpItem     dragRovHelp1 = new HelpItem( "Rover Adjustment",           dragRovHelpS, KeyEvent.VK_V );
  HelpItem     dragRovHelp2 = new HelpItem( "by Dragging",                dragRovHelpS, KeyEvent.VK_D );
  HelpItem       twistHelp  = new HelpItem( "Twist a Slice",                twistHelpS, KeyEvent.VK_T );
  HelpItem   roveArrowHelp  = new HelpItem( "with Arrow Keys",          roveArrowHelpS, KeyEvent.VK_A );
  HelpItem     roveGenHelp  = new HelpItem( "Rover General",              roveGenHelpS, KeyEvent.VK_R );
  HelpItem       driveHelp  = new HelpItem( "Drive Mode",                   driveHelpS, KeyEvent.VK_M );
  HelpItem        undoHelp  = new HelpItem( "Undo and Redo",                 undoHelpS, KeyEvent.VK_U );
  HelpItem  saveRecallHelp  = new HelpItem( "Save/Recall State",       saveRecallHelpS, KeyEvent.VK_S );
  HelpItem    saveFileHelp  = new HelpItem( "Save Files",                saveFileHelpS, KeyEvent.VK_F );
  HelpItem      colorsHelp  = new HelpItem( "Colors",                      colorsHelpS, KeyEvent.VK_C );
  HelpItem      labelsHelp  = new HelpItem( "Face Labels",                 labelsHelpS, KeyEvent.VK_L );
  HelpItem     layeredHelp  = new HelpItem( "Layered General",            layeredHelpS, KeyEvent.VK_L );
  HelpItem       tiledHelp  = new HelpItem( "Colored Tiles",                tiledHelpS, KeyEvent.VK_C );
  HelpItem        textHelp  = new HelpItem( "Text Display",                  textHelpS, KeyEvent.VK_T );
  HelpItem     centersHelp  = new HelpItem( "2 Centers",                  centersHelpS, KeyEvent.VK_2 );
  HelpItem      coordsHelp  = new HelpItem( "Coordinate Systems",          coordsHelpS, KeyEvent.VK_C );
  HelpItem      tattleHelp  = new HelpItem( "Tattles for Coordinates",     tattleHelpS, KeyEvent.VK_T );
  HelpItem     projectHelp  = new HelpItem( "Projection",                 projectHelpS, KeyEvent.VK_P );
  HelpItem    eyepointHelp1 = new HelpItem( "Eyepoint",                  eyepointHelpS, KeyEvent.VK_E );
  HelpItem    eyepointHelp2 = new HelpItem( "Eyepoint",                  eyepointHelpS, KeyEvent.VK_E );
  HelpItem       aboutHelp  = new HelpItem( "About MC3D Program",           aboutHelpS, KeyEvent.VK_A );

  HelpItem paramSub[]   = { paramHelp,   appearHelp,     scaleHelp,     eyepointHelp1                          };
  HelpItem dragSub[]    = { dragHelp,    tumbleHelp,     rotateHelp,    moveHelp,    animateHelp, dragRovHelp1 };
  HelpItem roverSub[]   = { roveGenHelp, dragRovHelp2,   roveArrowHelp, driveHelp                              };
  HelpItem layeredSub[] = { layeredHelp, tiledHelp,      textHelp                                              };
  HelpItem conceptSub[] = { centersHelp, coordsHelp,     tattleHelp,    projectHelp, eyepointHelp2             };
  HelpItem miscSub[]    = { labelsHelp,  saveRecallHelp, saveFileHelp,  colorsHelp                             };

  class SubHelp extends JMenu {
    HelpItem[] item;
    String name;

    SubHelp( String name, HelpItem[] item, int key ) {
      super( name );
      this.setText( name );
      this.setMnemonic( key );
      this.item = item;
    }
  }

  SubHelp helpSub[] = {
    new SubHelp( "Parameters",          paramSub,   KeyEvent.VK_P ),
    new SubHelp( "Dragging",            dragSub,    KeyEvent.VK_D ),
    new SubHelp( "Rover Adjustment",    roverSub,   KeyEvent.VK_R ),
    new SubHelp( "Layered-View Window", layeredSub, KeyEvent.VK_L ),
    new SubHelp( "Miscellaneous",       miscSub,    KeyEvent.VK_M ),
    new SubHelp( "Concepts",            conceptSub, KeyEvent.VK_C )
  };

/******** Parameter-Related Stuff ********/

  /* Class Param for describing the entries on the Parameters Menu
     and for keeping track of value of corresponding user-modifiable variables. */
  class Param extends JRadioButtonMenuItem implements ActionListener {
    float  min, max, val;
    String name, units;
    int    index;
    int    skipOrder;

    Param( String name, String units, float min, float max, float val, int key, int n ) {
      super( name );
      setMnemonic( key );
      setText( name );
      addActionListener( this );
      this.name  = name;
      this.min   = min;
      this.max   = max;
      this.val   = val;
      this.units = units;
      skipOrder  = n;
      this.setActionCommand( name );
    }

    /* Listen to the radio buttons on the Parameters menu. */
    public void actionPerformed( ActionEvent e ) {
      int i;
      curParam = index;
      ignore   = true;
      slider.setValue( Math.round( 720.f * ( val - min )/( max - min ) ) );
      numberField.setValue( Float.valueOf( val ) );
      ignore   = false;
      left_Label.setText( name.concat(":") );
      rightLabel.setText( units            );
    }
  }

  /* Strings for parameter names and units. */
  String      viewDist3S = "3D Viewing Distance";
  String            aziS = "Azimuth of Eyepoint";
  String           elevS = "Elevation of Eyepoint";
  String            rotS = "Rotation of 2D Image";
  String       faceSizeS = "Face Size";
  String    stickerSizeS = "Sticker Size";
  String     scaleLimitS = "Scale Limit";
  String         roverXS = "X - Rover Position";
  String         roverYS = "Y - Rover Position";
  String   roverHeadingS = "Heading of Rover";
  String roverViewAngleS = "View Angle of Rover";
  String    edgeWidth2DS = "2D Sticker-Edge Line-Width";
  String    edgeWidth1DS = "1D Sticker Separator Width";
  String   inFaceBrightS = "Brightness of In-Faces";
  String       outAlphaS = "Out-Face Alpha";
  String      twistTimeS = "Twist-Animation Duration";
  String        aniStepS = "Twist-Animation Step Size";
  String        rovStepS = "Rover-Animation Step Size";
  String  dragThresholdS = "Drag Threshold";
  String          widthS = "width";
  String         heightS = "height";

  /* Strings for parameter units. */
  String      viewDist3U = "MCS-units";
  String            aziU = "degrees";
  String           elevU = "degrees";
  String            rotU = "degrees";
  String       faceSizeU = "(fraction)";
  String    stickerSizeU = "(fraction)";
  String     scaleLimitU = "pixels/MCS";
  String         roverXU = "(fraction)";
  String         roverYU = "(fraction)";
  String   roverHeadingU = "degrees";
  String roverViewAngleU = "degrees";
  String    edgeWidth2DU = "pixels";
  String    edgeWidth1DU = "pixels";
  String   inFaceBrightU = "(fraction)";
  String       outAlphaU = "(transparency)";
  String      twistTimeU = "seconds";
  String        aniStepU = "seconds";
  String        rovStepU = "seconds";
  String  dragThresholdU = "pixels";
  String          widthU = "";
  String         heightU = "";

  /* Creation of Param objects */
  Param      viewDist3P = new Param(      viewDist3S,       viewDist3U,    3.f,  75.f,  5.3f, KeyEvent.VK_3, 0 );
  Param            aziP = new Param(            aziS,             aziU, -360.f, 360.f,  0.0f, KeyEvent.VK_A, 0 );
  Param           elevP = new Param(           elevS,            elevU,  -90.f,  90.f,  90.f, KeyEvent.VK_E, 0 );
  Param            rotP = new Param(            rotS,             rotU, -360.f, 360.f,  0.0f, KeyEvent.VK_R, 0 );
  Param       faceSizeP = new Param(       faceSizeS,        faceSizeU,    0.f,   1.f,  0.7f, KeyEvent.VK_F, 0 );
  Param    stickerSizeP = new Param(    stickerSizeS,     stickerSizeU,    0.f,   1.f,  0.5f, KeyEvent.VK_S, 0 );
  Param     scaleLimitP = new Param(     scaleLimitS,      scaleLimitU,    0.f, 400.f,  20.f, KeyEvent.VK_C, 0 );
  Param         roverXP = new Param(         roverXS,          roverXU,    0.f,   1.f,   .5f, KeyEvent.VK_X, 0 );
  Param         roverYP = new Param(         roverYS,          roverYU,    0.f,   1.f,   .5f, KeyEvent.VK_Y, 0 );
  Param   roverHeadingP = new Param(   roverHeadingS,    roverHeadingU, -360.f, 360.f,  0.0f, KeyEvent.VK_H, 0 );
  Param roverViewAngleP = new Param( roverViewAngleS,  roverViewAngleU,    5.f, 165.f,  0.0f, KeyEvent.VK_V, 0 );
  Param    edgeWidth2DP = new Param(    edgeWidth2DS,     edgeWidth2DU,    0.f,   4.f,   1.f, KeyEvent.VK_2, 0 );
  Param    edgeWidth1DP = new Param(    edgeWidth1DS,     edgeWidth1DU,    0.f,   2.f,   .3f, KeyEvent.VK_1, 1 );
  Param   inFaceBrightP = new Param(   inFaceBrightS,    inFaceBrightU,    0.f,   1.f,   1.f, KeyEvent.VK_B, 0 );
  Param       outAlphaP = new Param(       outAlphaS,        outAlphaU,    0.f,   1.f,  1.0f, KeyEvent.VK_O, 0 );
  Param      twistTimeP = new Param(      twistTimeS,       twistTimeU,    0.f,   4.f,   .5f, KeyEvent.VK_D, 0 );
  Param        aniStepP = new Param(        aniStepS,         aniStepU,    0.f,   .5f,  .05f, KeyEvent.VK_T, 0 );
  Param        rovStepP = new Param(        rovStepS,         rovStepU,    0.f,   .5f,   .1f, KeyEvent.VK_R, 0 );
  Param  dragThresholdP = new Param(  dragThresholdS,   dragThresholdU,    0.f, 360.f, 360.f, KeyEvent.VK_G, 0 );

  /* Array of references to all the Param objects for general treatment. */
  Param param[] = {
          viewDist3P,
                aziP,
               elevP,
                rotP,
           faceSizeP,
        stickerSizeP,
         scaleLimitP,
             roverXP,
             roverYP,
       roverHeadingP,
     roverViewAngleP,
        edgeWidth2DP,
        edgeWidth1DP,
       inFaceBrightP,
           outAlphaP,
          twistTimeP,
            aniStepP,
            rovStepP,
      dragThresholdP,
  };

  /* Separate arrays for the various Parameter submenus. */
  Param viewSub[] = {
          viewDist3P,
                aziP,
               elevP,
                rotP
  };

  Param sizesSub[] = {
           faceSizeP,
        stickerSizeP,
         scaleLimitP
  };

  Param rovStateSub[] = {
             roverXP,
             roverYP,
       roverHeadingP,
     roverViewAngleP
  };

  Param appearSub[] = {
        edgeWidth2DP,
        edgeWidth1DP,
       inFaceBrightP,
           outAlphaP
  };

  Param mechSub[] = {
          twistTimeP,
            aniStepP,
            rovStepP,
      dragThresholdP
  };

  class SubParam extends JMenu {
    Param[] paramA;
    String  name;
    String  mc3ID;

    SubParam( String name, String mc3ID, Param[] params, int key ) {
      super(        name );
      this.setText( name );
      this.mc3ID  = mc3ID;
      this.paramA = params;
      this.setMnemonic( key );
    }
  }

  SubParam parButsSub[] = {
    new SubParam( "3D Viewing Transformation", "View_Transformation:",     viewSub, KeyEvent.VK_3 ),
    new SubParam( "Sizes",                     "Sizes:",                  sizesSub, KeyEvent.VK_S ),
    new SubParam( "Roving Eye State",          "Rover:",               rovStateSub, KeyEvent.VK_R ),
    new SubParam( "Appearance of Stickers",    "Stickers:",              appearSub, KeyEvent.VK_A ),
    new SubParam( "Program Mechanics",         "Mechanics:",               mechSub, KeyEvent.VK_P )
  };

  int  curParam;                             // index into the param[] array for the currently selected button

  /* Change the value of a parameter. */
  void modifyParam( Param p, float v ) {
    p.val = Math.min( p.max, Math.max( p.min, v ) );
    if( param[curParam] == p ) numberField.setValue( Float.valueOf(v) );
  }

  /* Change the value of a parameter which is an angle.  Arg passed in radians, converted to degrees. */
  void modifyAngleP( Param p, double a ) {
    if( a> 2.*PI ) a -= 2.*PI;               // Handle wrap.
    if( a<-2.*PI ) a += 2.*PI;
    modifyParam( p, (float)( 180.*a/PI ) );
  }

/******** Configuration Support ********/

  class Config extends JMenuItem implements ActionListener {
    String name;
    String state;

    Config( int key, String name, String state_description ) {
      super(name);
      setText(name);
      this.name = name;
      state = state_description;
      setMnemonic(key);
      addActionListener(this);
    };

    /* The state strings are equivalent to save files with some lines removed. */
    public void actionPerformed( ActionEvent e ) {
      readState( new BufferedReader( new StringReader( state ) ) );
    }
  }

  Config config[] = {

    new Config( KeyEvent.VK_1, "1. Basic",            
                "View_Transformation: 3.5 0.0 90.0 0.0   \n" +
                "Sizes: 0.7 0.84 53.483093  \n" +
                "Rover: 0.5 0.5 0.0 50.0  \n" +
                "Stickers: 0.4 0.3 1.0 1.0  \n" +
                "Mechanics: 0.5 0.05 0.1 360.0  \n" +
                "Frame_Size: 581 500 \n" +
                "Center_Locations: 143 187 429 187 \n" +
                "View_Checkboxes: YNNNNYYYN \n" +
                "Toggle_Checkboxes: YYNNYNYNNNY \n" ),

    new Config( KeyEvent.VK_2, "2. Box with Lid Off",
                "View_Transformation:  5.0  0.0  90.0  0.0   \n" +
                "Sizes:  1.0  1.0  28.429838   \n" +
                "Rover:  0.5  0.5  0.0  50.0   \n" +
                "Stickers:  0.4  0.3  1.0  1.0   \n" +
                "Mechanics:  0.5  0.05  0.1  360.0   \n" +
                "Frame_Size:  191  570 \n" +
                "Center_Locations:  91 303   91 121 \n" +
                "View_Checkboxes:  YNNNNYYYN \n" +
                "Toggle_Checkboxes:  YYNNYNYNNNY "),

    new Config( KeyEvent.VK_3, "3. Basic - All Stickers Visible",
                "View_Transformation:  5.3  0.0  90.0  0.0   \n" +
                "Sizes:  0.7  0.5  80.89679   \n" +
                "Rover:  0.5  0.5  0.0  50.0   \n" +
                "Stickers:  0.0  0.3  1.0  1.0   \n" +
                "Mechanics:  0.5  0.05  0.1  360.0   \n" +
                "Frame_Size:  394  481 \n" +
                "Center_Locations:  193 178   193 178 \n" +
                "View_Checkboxes:  YNNNNYYYN \n" +
                "Toggle_Checkboxes:  YYNNYNYNNNY "),

    new Config( KeyEvent.VK_4, "4. Nearly Isometric",
                "View_Transformation:  12.0  136.0  36.0  180.0   \n" +
                "Sizes:  0.46  0.88  93.51172   \n" +
                "Rover:  0.5  0.5  0.0  50.0   \n" +
                "Stickers:  1.0  0.3  1.0  1.0   \n" +
                "Mechanics:  0.5  0.05  0.1  360.0   \n" +
                "Frame_Size:  375  497 \n" +
                "Center_Locations:  183 176   183 176 \n" +
                "View_Checkboxes:  YNNNNYYYN \n" +
                "Toggle_Checkboxes:  YYNNYYYNNNY "),

    new Config( KeyEvent.VK_5, "5. No Shrink.  Normal Cube.",
                "View_Transformation:  12.0  41.0  50.0  -53.0   \n" +
                "Sizes:  1.0  1.0  58.545082   \n" +
                "Rover:  0.5  0.5  0.0  50.0   \n" +
                "Stickers:  3.0  0.3  1.0  1.0   \n" +
                "Mechanics:  0.5  0.05  0.1  360.0   \n" +
                "Frame_Size:  382  474 \n" +
                "Center_Locations:  187 164   187 164 \n" +
                "View_Checkboxes:  YNNNNYYYN \n" +
                "Toggle_Checkboxes:  YYNNYNYNNNY "),

    new Config( KeyEvent.VK_6, "6. Out-Faces Transparent ",
                "View_Transformation:  7.8  135.0  35.0  0.0   \n" +
                "Sizes:  1.0  1.0  84.8146   \n" +
                "Rover:  0.5  0.5  0.0  50.0   \n" +
                "Stickers:  1.0  0.3  1.0  0.35   \n" +
                "Mechanics:  0.5  0.05  0.1  360.0   \n" +
                "Frame_Size:  500  640 \n" +
                "Center_Locations:  246 257   246 257 \n" +
                "View_Checkboxes:  YNNNNNYYN \n" +
                "Toggle_Checkboxes:  YYNNYNYNNNY "),

    new Config( KeyEvent.VK_7, "7. No Face Shrink - All Visible",
                "View_Transformation:  4.6  -144.0  80.0  144.0   \n" +
                "Sizes:  1.0  0.28  75.37396   \n" +
                "Rover:  0.5  0.5  0.0  50.0   \n" +
                "Stickers:  0.0  0.3  0.85  1.0   \n" +
                "Mechanics:  0.5  0.05  0.1  360.0   \n" +
                "Frame_Size:  406  524 \n" +
                "Center_Locations:  199 199   199 199 \n" +
                "View_Checkboxes:  YNNNNNYYN \n" +
                "Toggle_Checkboxes:  YYNNYNYNNNY "),

    new Config( KeyEvent.VK_8, "8. Layered-View Window",
                "View_Transformation:  5.0  0.0  90.0  0.0   \n" +
                "Sizes:  1.0  1.0  39.051975   \n" +
                "Rover:  0.5  0.5  0.0  50.0   \n" +
                "Stickers:  0.4  0.3  1.0  1.0   \n" +
                "Mechanics:  0.25  0.05  0.1  360.0   \n" +
                "Frame_Size:  288  664 \n" +
                "Center_Locations:  140 393   140 125 \n" +
                "View_Checkboxes:  YNNNNYYYY \n" +
                "Toggle_Checkboxes:  YYNNYNYNNNY "),

    new Config( KeyEvent.VK_9, "9. Rover in Middle",
                "View_Transformation:  12.0  136.0  36.0  180.0   \n" +
                "Sizes:  0.31805557  0.38055557  110.55458   \n" +
                "Rover:  0.5045045  0.5  58.99788  56.08108   \n" +
                "Stickers:  0.0  0.3  1.0  1.0   \n" +
                "Mechanics:  0.5  0.05  0.1  360.0   \n" +
                "Frame_Size:  452  555 \n" +
                "Center_Locations:  222 187   222 187 \n" +
                "View_Checkboxes:  YNYYNYYYN \n" +
                "Toggle_Checkboxes:  YYNYNYNNNNY "),

    new Config( KeyEvent.VK_A, "A. Rover Like Ortho", 
                "View_Transformation:  9.8  -130.39767  36.69465  115.90516   \n" +
                "Sizes:  0.33888888  0.84  107.3402   \n" +
                "Rover:  0.49019608  6.1154366E-4  0.0  31.79452   \n" +
                "Stickers:  0.0  0.3  1.0  1.0   \n" +
                "Mechanics:  0.5  0.05  0.1  360.0   \n" +
                "Frame_Size:  316  904 \n" +
                "Center_Locations:  77 106   227 72 \n" +
                "View_Checkboxes:  YYYYNYYYN \n" +
                "Toggle_Checkboxes:  NNNNYNYNNNY "),

    new Config( KeyEvent.VK_B, "B. 1D Stickers All Visible",
                "View_Transformation:  12.0  136.0  36.0  -349.68787   \n" +
                "Sizes:  0.097222224  0.20694445  104.13805   \n" +
                "Rover:  0.055306233  0.5037526  89.69965  5.0   \n" +
                "Stickers:  1.0  0.5  1.0  1.0   \n" +
                "Mechanics:  2.0  0.05  0.1  360.0   \n" +
                "Frame_Size:  911  274 \n" +
                "Center_Locations:  786 29   786 29 \n" +
                "View_Checkboxes:  YNYYNYYYN \n" +
                "Toggle_Checkboxes:  NNNNYYYNNNY ")
  };


/******** Frame (or Applet/application Window) Setup ********/

  /* Class MenuCommand for describing the individual commands on the Command Menu */
  class MenuCommand {
    AbstractAction action;
    String         name;
    int            cmdKey;
    int            accKey;

    MenuCommand( AbstractAction handler, String name, int cmdKey, int accKey ) {
      action      = handler;
      this.name   = name;
      this.cmdKey = cmdKey;
      this.accKey = accKey;
   }
  }

  MenuCommand command[] = {
    new MenuCommand( scramble,       "Scramble",      KeyEvent.VK_S, KeyEvent.VK_S ),
    new MenuCommand( init,           "Initialize",    KeyEvent.VK_I, KeyEvent.VK_I ),
    new MenuCommand( undo,           "Undo",          KeyEvent.VK_U, KeyEvent.VK_Z ),
    new MenuCommand( redo,           "Redo",          KeyEvent.VK_R, KeyEvent.VK_A ),
    new MenuCommand( cancel,         "Cancel Undo",   KeyEvent.VK_N, KeyEvent.VK_X ),
    new MenuCommand( save,           "Save State",    KeyEvent.VK_V, KeyEvent.VK_V ),
    new MenuCommand( recall,         "Recall State",  KeyEvent.VK_C, KeyEvent.VK_C ),
    new MenuCommand( colorAdjust,    "Adjust Colors", KeyEvent.VK_J, KeyEvent.VK_J ),
    new MenuCommand( mirror,         "Mirror Prefix", KeyEvent.VK_M, KeyEvent.VK_Q ),
    new MenuCommand( test,           "Test",          KeyEvent.VK_T, KeyEvent.VK_T ),
    new MenuCommand( exit,           "Exit",          KeyEvent.VK_E, KeyEvent.VK_E )
      };

  /* Class CheckBox for describing the individual check boxes on the View and Toggles Menus */
  class CheckBox extends JCheckBoxMenuItem {
    boolean initialState;
    String  name;
    int     skipOrder;

    CheckBox( String name, int key, boolean initialState, int n ) {
      super( name, initialState );
      this.name = name;
      this.initialState = initialState;
      this.setMnemonic( key );
      this.addActionListener( cbChange );
      skipOrder = n;
    }
  }

  /* View Menu boxes */
  CheckBox          twoDBox = new CheckBox( "2D Projection",                  KeyEvent.VK_2, true,  0 );
  CheckBox         orthoBox = new CheckBox( "Ortho 1D Projection",            KeyEvent.VK_O, false, 0 );
  CheckBox     roverProjBox = new CheckBox( "Rover 1D Projection",            KeyEvent.VK_R, false, 0 );
  CheckBox    roverFieldBox = new CheckBox( "Rover View Field",               KeyEvent.VK_V, false, 0 );
  CheckBox    tumbleAxisBox = new CheckBox( "Tumble Axis",                    KeyEvent.VK_T, false, 0 );
  CheckBox        labelsBox = new CheckBox( "Labels on Face Stickers",        KeyEvent.VK_L, true,  0 );
  CheckBox      modelTatBox = new CheckBox( "Model Coordinates Tattle",       KeyEvent.VK_M, true,  0 );
  CheckBox       faceTatBox = new CheckBox( "Face  Coordinates Tattle",       KeyEvent.VK_F, true,  0 );
  CheckBox       layeredBox = new CheckBox( "Layered-View Window",            KeyEvent.VK_W, false, 0 );

  /* Toggles Menu boxes */
  CheckBox       autoPosBox = new CheckBox( "auto-Position",                  KeyEvent.VK_P, true,  0 );
  CheckBox     autoScaleBox = new CheckBox( "auto-Scale",                     KeyEvent.VK_S, true,  0 );
  CheckBox    twistDir3DBox = new CheckBox( "3D Twist Direction",             KeyEvent.VK_3, false, 0 );
  CheckBox  viewUndoableBox = new CheckBox( "View  Changes Undoable",         KeyEvent.VK_V, true,  1 );
  CheckBox roverUndoableBox = new CheckBox( "Rover Changes Undoable",         KeyEvent.VK_R, false, 0 );
  CheckBox     driveModeBox = new CheckBox( "Rover Drive Mode",               KeyEvent.VK_D, true,  0 );
  CheckBox   lockCentersBox = new CheckBox( "Lock Centers",                   KeyEvent.VK_L, false, 0 );
  CheckBox   animateUndoBox = new CheckBox( "Animate Undo/Redo",              KeyEvent.VK_A, true,  0 );
  CheckBox   keyboardMCSBox = new CheckBox( "Model Coordinates for Keyboard", KeyEvent.VK_M, false, 0 );
  CheckBox    textDirMCSBox = new CheckBox( "MCS for Direction Indicators",   KeyEvent.VK_I, false, 0 );
  CheckBox      labelMCSBox = new CheckBox( "MCS for Face Labels",            KeyEvent.VK_F, false, 0 );
  CheckBox     highlightBox = new CheckBox( "Highlight Cubie's Stickers",     KeyEvent.VK_H, true,  0 );

  CheckBox vCheckBox[] = {
            twoDBox,
           orthoBox,
       roverProjBox,
      roverFieldBox,
      tumbleAxisBox,
          labelsBox,
        modelTatBox,
         faceTatBox,
         layeredBox
  };

  CheckBox tCheckBox[] = {
         autoPosBox,
       autoScaleBox,
      twistDir3DBox,
    viewUndoableBox,
   roverUndoableBox,
       driveModeBox,
     lockCentersBox,
     animateUndoBox,
     keyboardMCSBox,
      textDirMCSBox,
        labelMCSBox,
       highlightBox
  };

  JFrame              frame;
  JFormattedTextField numberField;
  NumberFormatter     formatter;
  JPanel              sliderPane;
  JSlider             slider;
  JPanel              textFieldPane;
  JLabel              left_Label, rightLabel;
  ButtonGroup         group;
  final double        PI = Math.PI;
  Random              random;
  javax.swing.Timer   aniTimer, delayTimer, roverTimer, driveTimer;
  Font                font = new Font( "SansSerif" , Font.BOLD, 14 );

  void makeFrame() {
    int i, j, k;

    pane  = new MainPane();
    frame = new JFrame("MC3D (main)");
    frame.addWindowListener( new WCloser() );
    frame.setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );

    JPanel contentPane = new JPanel();
    contentPane.setLayout( new BoxLayout( contentPane, BoxLayout.Y_AXIS ) );
    pane.setMinimumSize( new Dimension( 300,  70 ) );

    /* Make a menu bar */
    JMenuBar  menuBar  = new JMenuBar();
    frame.setJMenuBar( menuBar );

    /* Make Commands menu on menu bar. */
    JMenu     cMenu    = new JMenu( "Commands" );
    cMenu.setMnemonic( KeyEvent.VK_C );
    menuBar.add( cMenu );
    for(i=0; i<command.length; i++) {
      if( ( command[i].action == save  ||  command[i].action == recall )
          && !startMethod.equals("application")  ) continue;
      if( command[i].action == test  &&  !testOn ) continue;
      JMenuItem menuItem = new JMenuItem( command[i].name, command[i].cmdKey );
      if( i < command.length - 1 ) // Don't make "Exit" too easy.
        menuItem.setAccelerator( KeyStroke.getKeyStroke( command[i].accKey, ActionEvent.CTRL_MASK ) );
      menuItem.addActionListener( command[i].action );
      cMenu.add( menuItem );
    }

    /* Make ConfigSelect menu. */
    JMenu sMenu = new JMenu( "ConfigSelect" );
    sMenu.setMnemonic( KeyEvent.VK_S );
    menuBar.add( sMenu );
    for(i=0; i<config.length; i++) sMenu.add( config[i] );

    /* Make Toggles menu on menu bar. */
    JMenu tMenu = new JMenu( "Toggles" );
    tMenu.setMnemonic( KeyEvent.VK_T );
    for(i=0; i<tCheckBox.length; i++) {
      tCheckBox[i].setSelected( tCheckBox[i].initialState );
      tMenu.add( tCheckBox[i] );
    }
    menuBar.add( tMenu );

    /* Make View menu on menu bar. */
    JMenu vMenu = new JMenu( "View" );
    vMenu.setMnemonic( KeyEvent.VK_V );
    for(i=0; i<vCheckBox.length; i++) {
      vCheckBox[i].setSelected( vCheckBox[i].initialState );
      vMenu.add( vCheckBox[i] );
    }
    menuBar.add( vMenu );

    /* Make Parameters menu for menu bar and add radio buttons to group. */
    JMenu pMenu = new JMenu( "Parameters" );
    pMenu.setMnemonic( KeyEvent.VK_P );
    group = new ButtonGroup();
    for(i=0, k=0; i<parButsSub.length; i++) {
      for(j=0; j<parButsSub[i].paramA.length; j++) {
        Param p = parButsSub[i].paramA[j];
        group.add(p);
        parButsSub[i].add(p);
        p.index = k++;
      }
      pMenu.add( parButsSub[i] );
    }
    menuBar.add( pMenu );

    /* Make Help menu on menu bar. */
    JMenu hMenu = new JMenu( "Help" );
    hMenu.setMnemonic( KeyEvent.VK_H );
    hMenu.add( helpHelp  );
    hMenu.add( twistHelp );
    hMenu.add( undoHelp  );
    for(i=0; i<helpSub.length; i++ ) {
      for(j=0; j<helpSub[i].item.length; j++) {
        HelpItem h = helpSub[i].item[j];
        if( ( h!=saveRecallHelp && h!=saveFileHelp ) || startMethod.equals("application") ) helpSub[i].add(h);
      }
      hMenu.add( helpSub[i] );
    }
    hMenu.add( aboutHelp );
    menuBar.add( hMenu );

    /* Create the formatted text field and its formatter. */
    NumberFormat numberFormat = NumberFormat.getNumberInstance();
    formatter = new NumberFormatter(numberFormat);
    numberField = new JFormattedTextField(formatter);
    numberField.setColumns(5);
    numberField.addPropertyChangeListener( new TextFieldChangeListener() );

    /* Tell text field to ignore all the characters that don't make sense there. */
    InputMap iMap = numberField.getInputMap();        
    char c;
    for(c='a'; c<='z'; c++) iMap.put( KeyStroke.getKeyStroke(c), "none" ); // Any plain alphabetic.
    for(c='A'; c<='Z'; c++) iMap.put( KeyStroke.getKeyStroke(c), "none" );
    /* BFUDLR characters with any combination of modifiers. */
    for(i=0; i<6; i++) for(j=0; j<16; j++) iMap.put( KeyStroke.getKeyStroke( bChar[i], modsIE[j] ), "none" );
    int  mod[] = { InputEvent.ALT_DOWN_MASK, InputEvent.CTRL_DOWN_MASK, InputEvent.META_DOWN_MASK };
    for(i=0; i<rEvent.length; i++) for(j=0; j<3; j++) // arrow keys with modifiers,
      iMap.put( KeyStroke.getKeyStroke( rEvent[i], mod[j] ), "none" );
    for(i=0; i<command.length-1; i++)                 // command control keys.
      iMap.put( KeyStroke.getKeyStroke( command[i].accKey, InputEvent.CTRL_DOWN_MASK ), "none" );
    /* It seems silly to have to tell the numeric field to ignore all these characters that
       don't make sense there anyway.  But without the above code, the program does not work correctly. */

    /* Make text field pane. */
    textFieldPane = new JPanel( new FlowLayout() );
    textFieldPane.setMaximumSize( new Dimension( 3000, 20 ) );
    left_Label = new JLabel( "Face Size:", Label.RIGHT );
    rightLabel = new JLabel( "(fraction)", Label.LEFT );
    textFieldPane.add( left_Label  );
    textFieldPane.add( numberField );
    textFieldPane.add( rightLabel  );

    /* Make slider pane. */
    sliderPane = new JPanel( new FlowLayout() );
    sliderPane.setMaximumSize( new Dimension( 3000, 20 ) );
    slider = new JSlider( JSlider.HORIZONTAL, 0, 720, 360 );
    slider.setPreferredSize( new Dimension( 300, 20 ) );
    slider.addChangeListener( new SliderListener() );
    sliderPane.add( slider );

    /* Put everything in the content pane. */
    contentPane.add( pane          );
    contentPane.add( textFieldPane );
    contentPane.add( sliderPane    );
    frame.getRootPane().setContentPane( (Container)contentPane );

    /* Initialize the model. */
    makeCube();

    /* Start parameter adjustment GUI in a reasonable state and select first configuration. */
    for(i=0; i<param.length; i++) if( param[i] == faceSizeP ) break; // Find index of faceSize param.
    curParam = i;
    faceSizeP.setSelected( true );
    numberField.setValue( Float.valueOf( faceSizeP.val ) );

    /* Check for a file argument or a default settings file; or set builtin configuration #1. */
    if( !( startMethod.equals("application")  &&
           ( ( puzzleArgs.length > 0  &&  readStateFile( fix( new File( puzzleArgs[0] ) ) ) ) ||
             readStateFile("default.mc3")                                                         ) ) ) {
      config[0].actionPerformed( dummyEvent );
    }

    random     = new Random();
    aniTimer   = new javax.swing.Timer( 0, aniNext     ); // creation of the timer used for animation
    delayTimer = new javax.swing.Timer( 0, delayedShow ); // See just below this function.
    roverTimer = new javax.swing.Timer( 0, roverStep   ); // timer for adjusting rover with arrow keys
    driveTimer = new javax.swing.Timer( 0, driveStep   ); // timer for adjusting rover in drive mode
    roverChanging = false;
    delayTimer.setDelay( startMethod.equals("go") ? 1000 : 1 ); // Maybe wait a sec before show.
    delayTimer.start();
  }

  /* If we do not wait a while before attempting to show the frame, the browser can bury it. */
  AbstractAction delayedShow = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) {
        delayTimer.stop();
        frame.setVisible(true);
      }
    };

  ActionEvent dummyEvent = new ActionEvent( this, 0, "dummy" ); // Passed in some places, but never looked at.

  class WCloser extends WindowAdapter {
    public void windowClosed( WindowEvent e ) { exit.actionPerformed( dummyEvent ); }
    public void windowOpened( WindowEvent e ) { if( frame == null) makeFrame();     }
  }

  boolean ignore = false;   // Used below to prevent program-directed slider or text update from causing action.

  /* Listen to the slider. */
  public class SliderListener implements ChangeListener{
    public void stateChanged( ChangeEvent e ) {
      if( ignore ) return;
      int i   = curParam;
      float v = param[i].min + ( param[i].max - param[i].min ) * slider.getValue() / 720.f;
      startParam(i);
        numberField.setValue( Float.valueOf(v) );
      finishParam(i,v);
    }
  }

  /* Listen to the text field. */
  public class TextFieldChangeListener implements PropertyChangeListener {
    public void propertyChange( PropertyChangeEvent e ) {
      if( ignore ) return;
      Object value = numberField.getValue();
      if( value == null ) return;
      int i   = curParam;
      float v = Math.min( param[i].max, Math.max( param[i].min, ((Number)value).floatValue() ));
      startParam(i);
        numberField.setValue( Float.valueOf(v) ); // In case it was out of bounds.
        slider.setValue( Math.round( 720.f*( v-param[i].min )/( param[i].max - param[i].min ) ) );
      finishParam(i,v);
    }
  }

  /* Note that the following two routines used to bracket setValue's above prevent those setValue's
     from causing further execution of these listeners by manipulating boolean ignore. */
  void startParam( int i ) {
    ignore  = true;
    if( ( roverUndoableBox.isSelected()     &&
          ( param[i] == roverHeadingP   ||
            param[i] == roverViewAngleP ||
            param[i] == roverXP         ||
            param[i] == roverYP            )   ) ||
        (  viewUndoableBox.isSelected()     &&
          ( param[i] == viewDist3P      ||   
            param[i] == aziP            ||
            param[i] == elevP           ||
            param[i] == rotP               )   )    ) {
      if( whatsChanging != i ) curStack.push( new ChangeParam() );
      whatsChanging = i;
      return;
    }
    if( !autoScaleBox.isSelected() ) return;
    if(  param[i] == stickerSizeP     ||
         param[i] ==    faceSizeP     ||
         param[i] ==   viewDist3P        ) scaleDone = false;
  }

  void finishParam( int i, float v ) {
    ignore  = false;
    param[i].val = v;
    pane.repaint();
  }


/******** Puzzle State ********/

  /* Cube state variables.  The first group (before the blank line) correspond to user-modifiable
     parameters presented on the Parameters Menu.  The right justification here is due to some
     semiautomatic code generation related to the fact that this same rectangle of characters appears
     in 9 places, sometimes with "P", "S", or "U" suffixed to the individual identifiers.
     The values of most of these parameters are copied into these globals from the corresponding
     parameter objects at the beginning of fixView().  Others are copied where relevant.  The last two are
     actually copied nowhere, the Param object's .val member being used directly in one place only each.

     There are lots of other global variables scattered around, but when they only apply to a couple
     routines, they are usually declared near the code that deals with them. */

  float        viewDist3;             // 3D viewing distance: Cube is 3x3x3, centered at origin.
  double             azi;             // azimuth   of eyepoint relative to origin in MCS in radians
  double            elev;             // elevation of eyepoint relative to origin in MCS in radians
  double             rot;             // rotation angle for 2D display in radians
  float         faceSize;             // ratio of displayed face size to 'correct' size
  float      stickerSize;             // ratio of displayed sticker size to size resulting from faceSize
  float       scaleLimit;             // upper bound for computed scale:  Used when resulting image not too big.
  float           roverX;             // X position of roving eye as a fraction of available window width
  float           roverY;             // Y position of roving eye as a fraction of available window height
  double    roverHeading;             // heading of rover in degrees, where north (0 deg) corres. to upwards
  double  roverViewAngle;             // view angle of roving eye
  long         twistTime;             // duration of a 90deg twist for animation in milliseconds
  int            aniStep;             // time interval for twist animation step in milliseconds
  int            rovStep;             // time interval for rover animation step in milliseconds
  float      edgeWidth2D;             // for stroke when drawing edges of polygons in 2D display
  float      edgeWidth1D;             // for separators when drawing stickers in 1D displays
  float     inFaceBright;             // brightness of in-facing stickers relative to out-facing ones
  float         outAlpha;             // transparency for those stickers whose outside surface is visible
  float    dragThreshold;             // min dist (pixels) for movement with one button down to count as drag

  double  iniAzi, iniElev, iniRot;    // saved versions of eyepoint dir params used when rotating or tumbling
  V3F     eye3D          = new V3F(); // 3D eye point:  Set by fixview().  (to kO times viewDist3)
  V3F     kOverViewDist3 = new V3F(); // another normalization of kO.   (to kO times 1./viewDist3)
  V3F     iO             = new V3F(); // unit vectors establishing Orientation of cube for viewing:
  V3F     jO             = new V3F(); //    kO points towards eye point.  iO and jO are mapped to
  V3F     kO             = new V3F(); //    the positive x- and y-axes of the projected image.
  V3F     iI             = new V3F(); // Initial values of above saved while doing orientation adjustments
  V3F     jI             = new V3F(); //
  V3F     kI             = new V3F(); //
  V3F     oA[] = { iO, jO, kO };      // array of references to current orientation vectors
  V3F     iA[] = { iI, jI, kI };      // array of references to initial orientation vectors
  V3F     tumbleAxis     = new V3F(); // a unit vector in projection plane which is the axis of current tumble
  float   tumbleDisplace = 0.f;       // when tumbling, distance from downplace to current mouse cursor.  pixels.
  int     width = 0, height = 0;      // size of pane for 2D drawing
  float   pixRad;                     // radius in pixels depending on worst case of projection of 3D radius
  float   scale;                      // actual scale being used.  Equals scaleLimit if that's not too big.
  V2F     eyeF         = new V2F();   // float-type point version of roving eye position, screen coordinates
  V2I     eyeI         = new V2I();   // integer copy of above, screen coordinates
  V2I     in_Center    = new V2I();   // locations in pane of center positions relative to which stickers
  V2I     outCenter    = new V2I();   //    are plotted depending on whether the viewed side faces in or out
  boolean drawing      = true;        // false to suppress drawing; e.g., while doing a scramble
  boolean doSort       = true;        // true when the side which is visible of any sticker changes.
  boolean scaleDone    = false;       // true when auto-scaling has already happened.  Unset on relevant changes.
  boolean stateRecover = false;       // true when state is being recovered.  Prevents auto-Scale & -Position.


  final int OUT_LEFT        =  1;     // Possible values for vis member of StickerPlace below
  final int OUT_RIGHT       =  2;
  final int OUT_BOTH        =  3;
  final int INSIDE          =  4;

  /* Class StickerPlace for describing locations and other properties of all stickers. */
  class StickerPlace {

    V3F     pos;                // position of middle of facelet in 3-space without any face shrinking
    V3F     corner[];           // array of 3D corner points for sticker after face and sticker shrink
    int     face;               // index of the face on which this place lies
    int     axis;               // index of the axis perpendicular to this sticker
    int     colorI;             // color-index for color of sticker currently here - initially face index
    int     newCol;             // index of color at place being turned to here - while updating
    int     index;              // index of this object in place[]:  useful when using other arrays of places
    V2I     center;             // reference to whichever of outCenter or in_Center is center to plot relative to
    boolean facesOut;           // true when side of sticker seen from eyepoint is its outside relative to center
    boolean highlight;          // true when sticker needs highlighting.
    int     maxY;               // largest value of y for any polygon point.  Used for orthographic projection.
    float   roverDist;          // computed when doing roving eye view.  Square of dist of closest corner to eye.
    V2F     rp[];               // for rover projection:  copy of polygon vertices as displacements from eyeF
    int     vis[];              // vis[i] reflects visibility of rp[i] in rover view field.  OUT_LEFT, etc.
    V2F     polyF[];            // Floating version of the polygon for 2D plotting
    Polygon polyI;              // Integer  version of the polygon for 2D plotting
    int     slice[];            // slice[i] specifies on which slice perpendicular to the coordinate axis
                                //    with axis-index i lies this place.   negative: 0; center: 1; positive: 2

    /* The polygon points of a StickerPlace will be the corners of the corresponding sticker as
       projected onto the viewing plane and mapped to coordinates for graphics.  Floating point versions
       (polyF) are computed initially.  These are copied to the integer version (polyI) when drawn.
       The corner positions are computed by fixView() whenever view changes.  During animation, for
       the stickers in slices that are being turned, the motion tracker computes corners rotated
       about the twist axis for the turn angle current in the animation, projects them onto the
       xy-plane, does the viewing projection, and updates the sticker's polygon points.  It does not
       modify the sticker's corner values. */

    StickerPlace( int placeIndex,          // the index into the place[] array where reference to this is stored
                  int c1, int c2           // c1 and c2 are other 2 coordinate values besides that for face axis.
                  ) {
      int k, i1, i2;

      index       = placeIndex;
      face        = index/9;
      colorI      = face;
      axis        = face/2;
      i1          = ( axis == 0 ? 1 : 0 ); // other two axis-indices besides that of sticker-facing axis
      i2          = ( axis == 2 ? 1 : 2 ); // Note that i1 < i2.
      pos         = new V3F();
      pos.v[axis] = face%2>0 ? +1.5f : -1.5f;
      pos.v[i1]   = c1;
      pos.v[i2]   = c2;
      highlight   = false;
      slice       = new int[3];
      vis         = new int[4];
      corner      = new V3F[4];
      rp          = new V2F[4];
      polyF       = new V2F[4];
      polyI       = new Polygon();
      for(k=0; k<4; k++) {
        corner[k] = new V3F();
        rp[k] =     new V2F();
        polyF[k] =  new V2F();
        polyI.addPoint(0,0);                // Force allocation of 4 array slots for xpoints and ypoints.
      }
      for(k=0; k<3; k++) slice[k] = 1 + Math.round( .9f*pos.v[k] ); // rounding: -1.5 -> -1;  1.0 -> 1; etc.
    }
  }

  /* On initialization, 54 StickerPlace objects are created and references to them are put into the
     place[] and placeSI[] arrays.  These arrays are not modified during execution.
     The content of the placeO[], placeR[], and place2[] arrays is manipulated to achieve drawing sort order. */
  StickerPlace place[]   = new StickerPlace[ 54];  // the primary set
  StickerPlace placeSI[] = new StickerPlace[125];  // a spatially 'indexed' set of place references
  StickerPlace place2[]  = new StickerPlace[ 54];  // extra array of place references to sort for the 2D    view
  StickerPlace placeO[]  = new StickerPlace[ 54];  // extra array of place references to sort for the Ortho view
  StickerPlace placeR[]  = new StickerPlace[ 54];  // extra array of place references to sort for the Rover view


  /* spatialIndex() returns a unique index into the placeSI[] array for a reference to the StickerPlace
     object with a particular position in 3-space, which position is passed as the argument p.  It is sort
     of like a no-collision hash index based on the spatial coordinates of the sticker.
     Nulls exist in placeSI[] array. */
  int spatialIndex( V3F p ) {
    return     ( 2 + Math.round( 1.1f * p.v[0] ) ) +    // rounding: -1.5 -> -2;  1.0 -> 1; etc.
           5 * ( 2 + Math.round( 1.1f * p.v[1] ) ) +
          25 * ( 2 + Math.round( 1.1f * p.v[2] ) );
  }

  /* Index positions in place[] array correspond to following diagram:
   *                                                                             axis   default
   *             29 32 35                                                       & dir   color
   *             28 31 34
   *             27 30 33                              BFUDLR face codes:  Back   +y   Green
   *                     B                                                 Front  -y   Magenta
   *                                                                       Up     +z   Blue
   *   8  7  6   38 41 44   15 16 17       47 50 53                        Down   -z   Yellow
   *   5  4  3   37 40 43   12 13 14       46 49 52                        Left   -x   Cyan
   *   2  1  0   36 39 42    9 10 11       45 48 51                        Right  +x   Red
   *          L          D          R              U
   *
   *             18 21 24
   *             19 22 25                                                  |   -       +   |
   *             20 23 26                      Default Color Coding:    L  C  <--  x  -->  R  R
   *                     F                                              F  M  <--  y  -->  G  B
   *                                                                    D  Y  <--  z  -->  B  U
   *                            face  axis sticker                         |               |
   *    Corresponding Indices:   i     i/2  9*i+4 - face sticker
   *                            j/9   j/18   j    - any
   */


  final double colorComponents[][] = // fractional components for colors (tweaked to improve discrimination)
  /*{      cyan,         red,          magenta,        green,        yellow          blue,     }*/
    { {0.0,.95,.85}, {1.0,0.0,0.0}, {.90,0.0,.90}, {.15,1.0,0.0}, {.90,.90,0.0}, {0.0,0.0,1.0} };

  Color faceColor[]   = new Color[6];
  Color dimColor1D[]  = new Color[6];
  Color dimColor2D[]  = new Color[6];
  Color labelColor[]  = new Color[6];
  Color    bgColor    = new Color(0.f, 0.f, 0.f);
  Color    fgColor    = new Color(1.f, 1.f, 1.f);
  Color edge2Color    = new Color(0.f, 0.f, 0.f);
  Color edge1Color    = new Color(0.f, 0.f, 0.f);

  void createColors( Color[] a,      // array for Color objects in which to put references for the new objects
                     float f         // a factor (in (0,1]) by which to reduce brightness
                     ) {
    int i, j;
    float c[] = new float[3];
    for(i=0; i<6; i++) {
      for(j=0; j<3; j++) c[j] = (float)colorComponents[i][j];
      a[i] = new Color( f*c[0], f*c[1], f*c[2] );
    }
  }

  void makeCube() {
    int i, j, k=0, face;
    V3F p = new V3F();

    /* Create sticker places, specifying the positions of their centers in 3D.
       Sticker positions correspond to those of a 3x3x3 pile of 1x1x1 cubies centered at origin. */
    for(face=0; face<6; face++) { // Coordinate values off axis from {-1.0, 0.0, +1.0}; on axis, {-1.5, +1.5.}.
      for(i=-1; i<2; i++) {
        for(j=-1; j<2; j++) {
          place[k]  = new StickerPlace( k, i, j );
          placeO[k] = place[k];
          placeSI[ spatialIndex( place[k].pos ) ] = place[k++];
        }
      }
    }

    fixView();                        // This will set the polygon points for the places.
    createColors( faceColor,   1.f );
    for(i=0; i<6; i++) labelColor[i] = faceColor[i^1]; // Make labelColors be complementary.
    createColors( faceColor,   1.f ); // Recreate faceColors, so the types can be customized separately.
    createColors( dimColor1D, .85f );
  }


/******** Drawing, General ********/

  /* pane.paintComponent saves the passed Graphics context here as global, so we don't have to
     keep passing it around as an argument to all the various functions which need it.
     (Admittedly, it is strange to have a global with a 1-character name, but g occurs so often
     that this avoids unnecessary cluttering the code with many repetitions of a redundant name.) */
  Graphics2D g;

  /* The following arrays are data for writing or interpreting "BFUDLR" characters appropriately
     Also used for interpreting keyboard twist command in keyboard ActionEvent handler.  */
  final char bChar[] = { 'B', 'F', 'U', 'D', 'L', 'R' }; // BFUDLR order
  final char sChar[] = { 'b', 'f', 'u', 'd', 'l', 'r' }; // lower case version of above
  final char bFace[] = {  3,   2,   5,   4,   0,   1  }; // corresponding face indices
  final char fChar[] = { 'L', 'R', 'F', 'B', 'D', 'U' }; // face ordering of BFUDLR characters
  final char lChar[] = { 'l', 'r', 'f', 'b', 'd', 'u' }; // lower case version of above

  BasicStroke    wideStroke     = new BasicStroke( 1.5f );
  BasicStroke    mediumStroke   = new BasicStroke( 1.0f );
  BasicStroke    narrowStroke   = new BasicStroke( 0.5f );
  AlphaComposite acTransparent  = AlphaComposite.getInstance( AlphaComposite.SRC_OVER, .5f );
  AlphaComposite acTransparent2 = AlphaComposite.getInstance( AlphaComposite.SRC_OVER, .7f );
  AlphaComposite acOpaque       = AlphaComposite.getInstance( AlphaComposite.SRC_OVER );
  AlphaComposite acWithAlpha;

  boolean twoDOn, roverOn, orthoOn, lockOn;
  int     fullHeight;              // the last height reported from paintComponent()
  int     roverTop, orthoTop;
  int     roverMid, orthoMid;
  int     vSize1D = 55;            // vertical size for the 1D drawing areas
  int     sliderWidth;
  /* The program manipulates preferred size of slider, because system does not seem to override gracefully. */

  /* pane.paintComponent() is redirected to here.   w and h are width and height of the pane. */
  void paintPane( int w, int h ) {
    if( !animating && !dragging ) {
      sliderPane.repaint();        // For reasons I do not understand, these do not always
      textFieldPane.repaint();     //       get repainted when needed - thus this kludge.
    }

    /* Deal with change of pane size or other possible cause for automatic adjustments. */
    if(  w != width  ||  h != fullHeight          ||
          twoDOn !=         twoDBox.isSelected()  ||
         roverOn !=    roverProjBox.isSelected()  ||
         orthoOn !=        orthoBox.isSelected()  ||
          lockOn !=  lockCentersBox.isSelected()    ) {

      twoDOn      =         twoDBox.isSelected();
      roverOn     =    roverProjBox.isSelected();
      orthoOn     =        orthoBox.isSelected();
      lockOn      =  lockCentersBox.isSelected();

      scaleDone   = stateRecover; // May need to recompute scale.  Set scaleDone true unless recovering state.
      width       = w;
      height      = h;
      fullHeight  = h;            // This is the actual pane height.  Variable "height" is height of 2D area.

      /* Rover- and ortho-drawing area y-parameters.  Make sure off screen when not enabled. */
      if( roverOn ) height  -= vSize1D;
      roverTop = height;
      roverMid = roverTop + vSize1D/2;
      if( orthoOn ) height  -= vSize1D;
      orthoTop = orthoOn ? height : fullHeight;
      orthoMid = orthoTop + vSize1D/2;

      if( autoPosBox.isSelected() && !stateRecover ) positionCenters(); // Suppress when recovering state.

      int dsw = ( ( width < 310 ) ? width-10 : 300 );                   // Desired Slider Width
      if( Math.abs( dsw - sliderWidth ) > 1 ) {
        slider.setPreferredSize( new Dimension( dsw, 50 ) );
        sliderWidth = dsw;
        frame.setSize( 1+frame.getSize().width, frame.getSize().height );
        frame.dispatchEvent( new ComponentEvent( frame, ComponentEvent.COMPONENT_RESIZED ) );
      }
    }

    acWithAlpha = AlphaComposite.getInstance( AlphaComposite.SRC_OVER, outAlphaP.val );
    g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
    g.setBackground( bgColor );
    g.clearRect( 0, 0, width, fullHeight );
    g.setComposite( acOpaque );
    g.setFont(font);

    if( !animating ) fixView();
    paint2D();
    if( orthoOn ) paintOrtho();
    if( roverOn ) paintRover();
  }

  /* fixView() is called, except when animating, to recompute 3D sticker corner points
     and to reproject them to polygon points for drawing.  */
  void fixView() {

    /* Copy current parameter values from the Param objects. */
               azi =              aziP.val * PI/180.;
              elev =             elevP.val * PI/180.;
               rot =              rotP.val * PI/180.;
            roverX =           roverXP.val          ;
            roverY =           roverYP.val          ;
      roverHeading =     roverHeadingP.val * PI/180.;
    roverViewAngle =   roverViewAngleP.val * PI/180.;
       edgeWidth1D =      edgeWidth1DP.val          ;
         viewDist3 =        viewDist3P.val          ;
          faceSize =         faceSizeP.val          ;
       stickerSize =      stickerSizeP.val          ;
        scaleLimit =       scaleLimitP.val          ;

    doSort = true;

    /* Compute orientation vectors and eyepoint based on current azimuth, elevation, and rotation. */
    iO.set( (float)Math.cos( azi ), -(float)Math.sin( azi ), 0.f );
    kO.set( -iO.v[1], iO.v[0], 0.f ).times( (float)Math.cos( elev ) ).v[2] = (float)Math.sin(elev);
    jO.copy(kO).cross(iO);
    iO.times( (float)Math.cos( rot ) ).plus( jO.times( -(float)Math.sin( rot ) ) );
    jO.copy(kO).cross(iO);
    eye3D.copy(kO).times( viewDist3 );
    kOverViewDist3.copy(kO).times( 1.f/viewDist3 );

    /* Set the roving eye position - both versions. */
    eyeI.copy( eyeF.set( roverX * width, ( 1.f - roverY ) * height ) );

    /* Compute maxScale.  scale <= maxScale assures no sticker is drawn outside pane. */
    float radius, maxScale;
    radius     = faceSize * ( 2.f + stickerSize ) * .707107f; // from center of face to farthest sticker corner
    radius     = (float)Math.sqrt( 2.25 + radius*radius );    // now from center of cube
    double rat = radius/viewDist3;
    pixRad     = radius/(float)Math.sqrt( 1. - rat*rat );     // still in MCS-units
    maxScale   = .95f * Math.min( borderDist( in_Center ), borderDist( outCenter ) ) / pixRad;

    /* Set scale, setting scaleLimit also if auto-scale enabled.  */
    if( !scaleDone  &&  autoScaleBox.isSelected() ) {
      modifyParam( scaleLimitP, maxScale );    // the max as of now
      scaleLimit = maxScale;
    }
    scaleDone    = true;
    stateRecover = false;                      // Now past danger time for fouling up saved state.
    scale = Math.min( maxScale, scaleLimit );  // maxScale need no longer be what it was when !scaleDone.
    pixRad = scale*pixRad;                     // now in pixel units for display

    /* Recompute and reproject sticker corners. */
    V3F c       =  tV21;                       // for forming the corners
    V3F t       =  tV22;
    V3F edge    =  tV23;                       // displacement vectors from one corner of sticker to next
    int i, j;

    for(i=0; i<54; i++) {
      float f;
      int   face = i/9;
      int   axis = i/18;
      int   o1   = ( (face%2 > 0) ? 2 : 1 );
      int   i1   = (axis+o1  )%3;              // 1st other axis besides sticker-facing axis
      int   i2   = (axis+3-o1)%3;              // 2nd other axis besides sticker-facing axis
      /* Resulting direction for traversing corner points is clockwise as viewed from outside cube. */

      edge.v[axis] = 0.f;
      edge.v[i1]   = faceSize * stickerSize;
      edge.v[i2]   = 0.f;
      c.zero().v[axis]  = place[i].pos.v[axis];                   // Other two coords still 0., i.e. face center.
      place[i].facesOut = ( c.dot( t.copy( eye3D ).minus(c) ) > 0.f );
      place[i].center   = place[i].facesOut ? outCenter : in_Center;
      c.plus( t.copy( place[i].pos ).minus(c).times(faceSize) );  // Now c is faceSize-adjusted sticker-position.
      c.v[i1] -= .5f * faceSize * stickerSize;                    // Displace to a corner - neg, neg
      c.v[i2] -= .5f * faceSize * stickerSize;
      for(j=0; j<4; j++) {                           // Make three more corners from adjusted sticker position.
        place[i].corner[j].copy(c);                  // Store a corner.
        place[i].polyF[j].setProj(c).flip().times( scale ).plus( place[i].center );
        c.plus(edge);                                // Move to next corner.
        f          =  edge.v[i1];                    // Turn edge vector 90 degrees.
        edge.v[i1] = -edge.v[i2];                    // non-0 component of the i1/i2 pair is pos. on 1st 2 steps.
        edge.v[i2] =  f;
      }
    }
  }

  void positionCenters() {
    int eSize  = Math.min( width, height );            // effective size
    in_Center.x = outCenter.x =  width/2;              // Start setting box and top centers to center of panel.
    in_Center.y = outCenter.y = height/2;
    if( lockCentersBox.isSelected() ) return;
    if(        3* width > 4*height ) {                 // If oblong wide (squat), separate horizontally.
      if(  width/2 < height ) eSize =  width/2;
      in_Center.x -= eSize/2;
      outCenter.x += eSize/2;
    } else if( 3*height > 4* width ) {                 // If oblong narrow (tall), separate vertically.
      if( height/2 <  width ) eSize = height/2;
      in_Center.y += eSize/2 - 10;
      outCenter.y -= eSize/2 + 10;
    }
  }

  /* Return smallest distance of point from any edge of pane defined by width and height */
  int borderDist( V2I p ) {
    return Math.min( Math.min(  p.x, width  - p.x  ),
                     Math.min(  p.y, height - p.y  ) );
  }


/******** Paint 2D Drawing Area ********/

  V3F  tV11 = new V3F(), tV13 = new V3F(), tV17 = new V3F();
  V2F  tF11 = new V2F(), tF12 = new V2F(), tF13 = new V2F();
  V2I  tI11 = new V2I();

  L2F line  = new L2F();
  float lastEdgeWidth    = -1.f;
  float lastInFaceBright = -1.f;
  BasicStroke edgeStroke;

  void paint2D() {
    int i, j, k;
    V3F t = tV11, v = tV13;
    V2F u = tF11, p1 = tF12, p2 = tF13;

    /* Show "BFUDLR", twice. */
    int rx = width-90;
    int lx = 6;
    for(i=0; i<6; i++) {
      if( modelTatBox.isSelected() ) {
        j = 9*bFace[i] + 4;     // index in place[] of face sticker for this face
        if( animating && sliceGo[1] && ( place[j].slice[ twistAxis ] == 1 ) ) g.setColor( Color.GRAY );
        else g.setColor( faceColor[ place[j].colorI ] );
        g.drawChars( sChar, i, 1, lx, height-10 );
        lx += 14;
      }
      if( faceTatBox.isSelected() ) {
        g.setColor( faceColor[ bFace[i] ] );
        g.drawChars( bChar, i, 1, rx, height-10 );
        rx += 14;
      }
    }

    /* Make orientation tattle on left for cube's model coordinate system - MCS. */
    g.setStroke( wideStroke );
    if( modelTatBox.isSelected() ) {
      for(i=0; i<3; i++) {
        axI[i]    = i;
        colorI[i] = place[ 13 + 18*i ].colorI;
      }
      apex.set( -1.f, -1.f, -1.f );
      drawTattle( 23, height-47, true );
    }

    /* Make orientation tattle on right for cube's face coordinate system - FCS. */
    /* Find index j in place[] corresponding to face _initially_ pointing in positive dir. for FCS axis i. */
    if( faceTatBox.isSelected() ) {
      for(i=0; i<3; i++) {
        for(j=4; j<54; j+=9) if( place[j].colorI == 2*i+1 ) break;
        int a     = place[j].axis;
        axI[i]    = a;
        apex.v[a] = ( place[j].pos.v[a] > 0. ) ? -1.f : 1.f;
        colorI[i] = 2*i+1;
      }
      drawTattle( width-25, height-47, false );
    }

    /* Draw the stickers.  First, sort them when necessary. */
    if( twoDOn ) {
      StickerPlace negFace,   posFace;
      StickerPlace far_Face,  nearFace;
      int          far_Slice, nearSlice; // Indices into slice[] array of a StickerPlace.
      i2 = 0;                            // Indexes place2[].  Shared with queueRadial, which see.

      /* When animating for a single-slice twist, we have the tough case for ordering the stickers.
         The last queue-order is still OK unless doSort == true.  */
      if( doSort &&  animating  &&  ( sliceGo != wholeCase ) ) {

        simpleSort = false;

        /* Determine which face with twist axis as axis lies farthest away from eyepoint.
           v is set to the unit vector along twist axis pointing towards nearer face.
           As a point, v is on the interface between the center slice and the nearer slice. */
        negFace  = place[ 18*twistAxis +  4 ];
        posFace  = place[ 18*twistAxis + 13 ];
        if( posFace.pos.sqDist( eye3D ) > negFace.pos.sqDist( eye3D ) ) {
          far_Face  = posFace;
          nearFace  = negFace;
          far_Slice = 2;
          nearSlice = 0;
          v.zero().v[ twistAxis ] = -1.f;
        } else {
          far_Face  = negFace;
          nearFace  = posFace;
          far_Slice = 0;
          nearSlice = 2;
          v.zero().v[ twistAxis ] =  1.f;
        }

        /* Queue far face (which IS in-facing) - and near face IF it is in-facing. */
        for(  i = far_Face.index - 4; i < ( far_Face.index + 5 ); i++) place2[i2++] = place[i];
        if( !nearFace.facesOut )
          for(i = nearFace.index - 4; i < ( nearFace.index + 5 ); i++) place2[i2++] = place[i];

        /* Queue radially facing (relative to twist axis) stickers in far face, in-facing ones first. */
        queueRadial( far_Slice, true  );
        queueRadial( far_Slice, false );

        /* Queue radials of last 2 slices with order depending on which way faces the interface between them. */
        if( t.copy(v).minus( eye3D ).dot(v) > 0.f ) { // Test is true if interface, center->nearface, faces away.
          queueRadial( nearSlice, true  );
          queueRadial(         1, true  );
          queueRadial( nearSlice, false );
          queueRadial(         1, false );
        } else {
          queueRadial(         1, true  );
          queueRadial(         1, false );
          queueRadial( nearSlice, true  );
          queueRadial( nearSlice, false );
        }

        /* Queue near face if it is out-facing. */
        if( nearFace.facesOut )
          for(i = nearFace.index - 4; i < ( nearFace.index + 5 ); i++) place2[i2++] = place[i];

      } else if( doSort ) {   // Twisting whole cube or not animating.  Just queue on basis of in- or out-facing.
        simpleSort = true;
        for(i=0; i<54; i++) if( !place[i].facesOut ) place2[i2++] = place[i];
        for(i=0; i<54; i++) if(  place[i].facesOut ) place2[i2++] = place[i];
      }
      doSort = false;

      /* React to changed edge width. */
      edgeWidth2D = edgeWidth2DP.val;
      if( lastEdgeWidth != edgeWidth2D ) edgeStroke = new BasicStroke( edgeWidth2D );
      lastEdgeWidth = edgeWidth2D;
      g.setStroke( edgeStroke );
      boolean drawEdges = edgeWidth2D > .399f;

      /* React to changed brightness for in-facing stickers. */
      inFaceBright = inFaceBrightP.val;
      if( lastInFaceBright != inFaceBright ) createColors( dimColor2D, inFaceBright );
      lastInFaceBright = inFaceBright;

      /* NOW draw them!  In the computed order. */
      for(i=0; i<54; i++) {
        StickerPlace p = place2[i];
        for(j=0; j<4; j++) {    // Get integer polygon corner values for drawing.
          p.polyI.xpoints[j] = (int)p.polyF[j].x;
          p.polyI.ypoints[j] = (int)p.polyF[j].y;
        }
        p.polyI.invalidate();
        g.setComposite(  p.facesOut ? acWithAlpha           : acOpaque               );
        g.setColor(      p.facesOut ? faceColor[ p.colorI ] : dimColor2D[ p.colorI ] );
        g.fillPolygon( p.polyI );
        if( drawEdges) { g.setColor( edge2Color ); g.drawPolygon( p.polyI ); }
        j = p.index;
        if( ( simpleSort && j%9!=8 ) || ( !simpleSort && j%9!=4 ) || !labelsBox.isSelected() ) continue;
        /* Label face sticker from back when j%9==4.  For the simple sorts, this assures that edges of
           other stickers in the same face do not obscure the label when face is seen nearly edge on. */
        if( simpleSort ) j-=4;
        if( labelMCSBox.isSelected() && animating && sliceGo[1] && ( place[j].slice[twistAxis]==1 ) ) continue;
        g.setComposite( acOpaque );
        g.setColor( labelColor[ place[j].colorI ] );
        v.setCurPos(j);
        V2I c = place[j].center;
        if( labelMCSBox.isSelected() ) g.drawChars( lChar, j/9,             1, (int)v.xP(c)-5, (int)v.yP(c)+6 );
        else                           g.drawChars( fChar, place[j].colorI, 1, (int)v.xP(c)-5, (int)v.yP(c)+6 );
      }
    }

    /* Draw highlighting. */
    g.setStroke( wideStroke );
    g.setColor( Color.WHITE );
    g.setComposite( acOpaque );
    for(i=0; i<54; i++) if( place[i].highlight ) g.drawPolygon( place[i].polyI );

    g.setComposite( acTransparent2 ); // For the "+", the tumble axis, or the rover field below.
    g.setColor( fgColor );

    /* Make a little "+" mark for start of drag, if tumbling in progress. */
    if( dragging  &&  ( dragFor == TUMBLE ) ) drawPlus( downPlace );

    /* If 2D drawing off, mark center positions. */
    if( !twoDOn ) { drawPlus( in_Center ); drawPlus( outCenter ); }

    /* Show tumble axis. */
    if( tumbleAxisBox.isSelected()  &&  dragging  &&  dragFor==TUMBLE ) {
      u.setProj( v.copy( tumbleAxis ).times( Math.min( tumbleDisplace, pixRad ) ) ).flip();
      g.draw( line.set( p1.copy( downCenter ).plus(u), p2.copy( downCenter ).minus(u) ) );
      u.setProj( v.copy( tumbleAxis ).times( Math.min( tumbleDisplace, 5.f ) ) ).rot().flip();
      g.draw( line.set( p1.copy( downCenter ).plus(u), p2.copy( downCenter ).minus(u) ) );
    }

    /* Show rover field of view */
    if( roverFieldBox.isSelected() ) {
      float size = Math.min( width, height );
      g.draw( line.set( eyeF, u.setDir( roverHeading - roverViewAngle/2. ).flip().times(size).plus(eyeF) ) );
      g.draw( line.set( eyeF, u.setDir( roverHeading + roverViewAngle/2. ).flip().times(size).plus(eyeF) ) );
    }
  }

  /* Draw a little plus mark at given drawing-space point. */
  void drawPlus( V2I p ) {
    g.drawLine( p.x-3, p.y,   p.x+3, p.y   );
    g.drawLine( p.x,   p.y-3, p.x,   p.y+3 );
  }

  /* Data used for making orientation tattles. */
  int  axI[]    =   new int[3];         // axI[i] is the MCS axis-index for the axis along which axis i points.
  int  colorI[] =   new int[3];         // colorI[i] is color of face sticker facing in pos. dir. for axis i.
  V3F  apex     =   new V3F();          // Location of corner cubie where the three lines join.
  V2F  endPt[]  = { new V2F(), new V2F(), new V2F(), new V2F() };
  V3F  ePos[]   = { new V3F(), new V3F(), new V3F(), new V3F() };
  char aChar[]  = {'x','y','z'};

  /* Draw an orientation tattle. */
  void drawTattle( int x, int y, boolean forMCS ) {
    int   i, iFar = 0;
    float dist, maxDist = -99.f;
    V3F   t = tV17;

    for(i=0; i<4; i++) {                                       // Compute endpoints of the vectors for each axis.
      ePos[i].copy( apex );
      if( i<3 ) {                                              // endPt[3] is for apex itself.
        ePos[i].v[ axI[i] ] = -apex.v[ axI[i] ];               // Negate coordinate for corresponding axis.
      }
      if( !forMCS && animating && sliceGo[1] ) ePos[i].turn();
      dist = -kO.dot( ePos[i] );
      if( dist > maxDist ) { maxDist = dist; iFar = i; }
      t.copy( ePos[i] ).minus( eye3D );                        // Make orthogonal proj. instead of perspective.
      endPt[i].set( x + (int)( 11.f * iO.dot(t) ),
                    y - (int)( 11.f * jO.dot(t) ) );
    }
    if( iFar != 3 )
      t.zero().v[ axI[iFar] ] = -1.5f * apex.v[ axI[iFar] ];   // t is loc. of face sticker on face with iFar.
    boolean farPointsAway = (  (iFar != 3 )  &&  !placeSI[ spatialIndex(t) ].facesOut  );
    if( farPointsAway ) drawChar( iFar, forMCS );              // Draw its char under.
    for(i=0; i<3; i++) {
      g.setColor( (  i == iFar  ||  !farPointsAway  ) ? Color.WHITE : faceColor[ 1^colorI[iFar] ] );
      g.draw( line.set( endPt[3], endPt[i] ) );
      if(  i != iFar  ||  !farPointsAway  ) drawChar( i, forMCS );
    }
  }

  /* Draw the char for axis i in the color specified by colorI[i]. */
  void drawChar( int i, boolean forMCS ) {
    g.setColor( ( forMCS && animating && sliceGo[1] && i!=twistAxis ) ? Color.GRAY : faceColor[ colorI[i] ] );
    g.drawChars( aChar, i, 1, (int)endPt[i].x - 3, (int)endPt[i].y + 4 );
  }

  boolean simpleSort = true;  // true when all 9 stickers of each face are queued consecutively in place[] order

  /* queueRadial() is a utility for putting places into the place2[] array.
     It only does stickers which face out radially relative to the twist axis and which belong to "slice".
     If argument "doInFacing" is true, only in-facing stickers are queued.  If false, out-facers. */

  int i2;        // An index for the place2[] array for where to queue.  Also manipulated in paint2D.

  void queueRadial( int slice, boolean doInFacing ) {
    int i;
    for(i=0; i<54; i++)
      if( ( doInFacing ^ place[i].facesOut ) &&                // Most likely test to fail.
          ( place[i].slice[ twistAxis ] == slice ) &&
          ( Math.abs( place[i].pos.v[ twistAxis ] ) < 1.2f ) ) // Eliminate non-radially-facing stickers.
        place2[i2++] = place[i];
  }

  V3F tV21 = new V3F(), tV22 = new V3F(), tV23 = new V3F();

/******** Paint Orthographic Projection ********/

  V3F tV31 = new V3F();
  int mid1D;                    // either orthoMid or roverMid.  Used by drawEdge() for which area to draw in.

  void paintOrtho() {
    if(       yBuf.length < width  ) yBuf      = new float[width];
    if(  orthoIBuf.length < width  ) orthoIBuf = new   int[width];
    iBuf = orthoIBuf;
    mid1D = orthoMid;
    g.setBackground( bgColor );
    g.setComposite( acOpaque );
    g.setStroke( wideStroke );
    g.clearRect( 0, orthoTop, width, orthoTop+vSize1D );
    g.setColor( Color.GRAY );
    g.drawLine( 5, orthoTop, width-5, orthoTop );
    setSepStroke();

    int i, j, k, l, m=0;
    for(i=0; i<width;  i++) {
      yBuf[i] = 0.f;
      iBuf[i] = 54;
    }
    for(i=0; i<54; i++) {                             // Set maxY member for all the sticker places.  For sort.
      int maxY = 0;
      for(j=0; j<4; j++) maxY = Math.max( maxY, place[i].polyI.ypoints[j] );
      place[i].maxY = maxY;
    }
    Arrays.sort( placeO, orthoCompare );              // Draw from front to back, so hidden stuff never drawn.
    for(i=0; i<54; i++) {
      StickerPlace p = placeO[i];                     // the sticker Place we are working on
      for(k=0; k<4; k++) {                            // Copy sticker's polygon points in counterclockwise order.
        j = ( p.facesOut ? 3-k : k );                 // Opposite expectation because y-coords upside down.
        px[j] = p.polyI.xpoints[k];
        py[j] = p.polyI.ypoints[k];
      }
      int n=0;
      for(l=0; l<4; l++) if( px[l] < px[(l+1)%4] ) n++;      // Find number of edges which show.
      if(n==0) continue;                                     // all same x-value  (It happens.)
      for(l=4; l<8; l++) if( px[l%4] < px[(l+1)%4]  ) break; // Find first corner of Leftmost edge which shows.
      while( px[(l+3)%4] < px[l%4] ) l--;                    // (IMO, % does not do right thing for negative
      l = l%4;                                               //        arguments, so they are avoided here.)
      Color bc = faceColor[  p.colorI ];                     // Bright Color
      Color dc = dimColor1D[ p.colorI ];                     // Dim    Color
      if(n>0) drawEdge(p, px[ l     ], (float)py[ l     ], px[(l+1)%4], (float)py[(l+1)%4], ( n==3 ? dc : bc ) );
      if(n>1) drawEdge(p, px[(l+1)%4], (float)py[(l+1)%4], px[(l+2)%4], (float)py[(l+2)%4], ( n==2 ? dc : bc ) );
      if(n>2) drawEdge(p, px[(l+2)%4], (float)py[(l+2)%4], px[(l+3)%4], (float)py[(l+3)%4],          dc        );
      drawSep( px[ l     ], (float)py[ l     ] );
      drawSep( px[(l+n)%4], (float)py[(l+n)%4] ) ;

      if( p.highlight ) {
        g.setColor( Color.WHITE );
        g.setStroke( wideStroke );
        g.drawLine( px[l], mid1D-5, px[(l+n)%4], mid1D-5 );
        g.drawLine( px[l], mid1D+4, px[(l+n)%4], mid1D+4 );
        g.setStroke( sepStroke );
      }
    }

    if( labelsBox.isSelected() ) {
      for(i=0; i<6; i++) {
        StickerPlace p = place[9*i+4];
        int x = (int)tV31.setCurPos( 9*i+4 ).xP( p.center );
        int y = ( p.facesOut ? orthoMid-12 : orthoMid+12 );
        g.setColor( faceColor[ p.colorI ] );
        g.drawChars( fChar, p.colorI, 1, x-5, y+6 );
      }
    }
  }

  public final Comparator<StickerPlace> orthoCompare = new Comparator<StickerPlace>() {
      public int compare( StickerPlace o1, StickerPlace o2 ) {
        StickerPlace p1 = o1;
        StickerPlace p2 = o2;
        return ( p1.maxY == p2.maxY ) ? ( p1.facesOut ? -1 : 1 ) : ( p1.maxY < p2.maxY  ? 1 : -1 );
      }
    };

  /* Data used by drawEdge() below. */
  float    yBuf[] = {0.f};           // y-buffer to avoid drawing portions of stickers behind already drawn ones.
  int      iBuf[];                   // Indentification buffer.  Index of sticker which shows at this x.
  int roverIBuf[] = {0};
  int orthoIBuf[] = {0};
  int        px[] = new int[4];      // Used to copy xpoints and ypoints from a sticker's integer polygon.
  int        py[] = new int[4];      //    Order corresponds to counterclockwise in the 2D display.

  /* drawEdge figures out what portions of an edge will be visible and draws them. */
  void drawEdge( StickerPlace p,                 // the sticker an edge of which we are drawing
                 int cX, float cY,               // coordinates of left  endpoint
                 int rX, float rY,               // coordinates of right endpoint
                 Color color                     // which color array to take color from
                 ) {
    /* cX and cY, though they start as coordinates for left end point, are used to maintain the current
       values of the x-position and y-value where we are working. */
    int   i;
    float incY = ( rY - cY ) / ( rX - cX );      // slope as y-change per pixel of x
    if( rX<=0 ) return;
    rX = Math.min( rX, width-1 );                // x-value at right end of edge
    if( cX<0 ) { cY -= cX*incY; cX=0; }          // Move onto screen.
    g.setColor( color );
    while( true ) {
      while( cX <= rX  &&  ( cY < yBuf[cX]  ||  ( cY == yBuf[cX]  &&  !p.facesOut ) ) ) {
        cX++;                                    // not showing
        cY += incY;
      }
      if( cX>rX ) break;
      int start = cX;                            // A part that shows starts here.
      while( cX <= rX  &&  ( cY > yBuf[cX]  ||  ( cY == yBuf[cX]  &&   p.facesOut ) ) ) {
        yBuf[cX] = cY;                           // Update y-buffer for where we are going to draw.
        iBuf[cX] = p.index;
        cX++;
        cY += incY;
      }
      g.fillRect( start, mid1D-4, cX-start, 8 ); // Now, no show at cX.
      if( cX>rX ) break;
    }
    /* The "equals" comparison above on floats actually makes sense because, when it is relevant,
       the y values in the y-buffer were generated by the very same iteration as is generating cY now. */
  }

  BasicStroke sepStroke = new BasicStroke();
  float lastEdgeWidth1D = -1.f;

  /* Set stroke for sticker separators, making a new BasicStroke when necessary. */
  void setSepStroke() {
    if( lastEdgeWidth1D != edgeWidth1D ) sepStroke = new BasicStroke( edgeWidth1D );
    lastEdgeWidth1D = edgeWidth1D;
    g.setStroke( sepStroke );
  }

  /* Draw a vertical black line at x if y will show. */
  void drawSep( int x, float y ) {
    if( edgeWidth1D < .1  ||  x<0  ||  x >= width ) return;
    g.setColor( edge1Color );
    if( y > yBuf[x]-.1f ) g.drawLine( x, mid1D+3, x, mid1D-4 );
    yBuf[x] += .1f; // Protect it a bit.
  }


/******** Paint Roving Eye Projection ********/

  V2F hf = new V2F(), hn = new V2F(); // for Forward and Normal (to right) unit vecs for rover Heading
  V2F lf = new V2F(), ln = new V2F(); // for Forward and Normal (inwards)  unit vecs for Left  side of view field
  V2F rf = new V2F(), rn = new V2F(); // for Forward and Normal (inwards)  unit vecs for Right side of view field

  float viewDist2;                    // viewing distance for rover projection.  Make projection fill width.
  int ve[] = new int[3];              // indices of the Visible Edges
  V2F v1[] = { new V2F(), new V2F(), new V2F(), new V2F() }; // v1[k] is first  endpoint of kth edge.
  V2F v2[] = { new V2F(), new V2F(), new V2F(), new V2F() }; // v2[k] is second endpoint of kth edge.

  V2F tF43 = new V2F(), tF44 = new V2F();

  void paintRover() {
    if(       yBuf.length < width  ) yBuf      = new float[width];
    if(  roverIBuf.length < width  ) roverIBuf = new   int[width];
    iBuf = roverIBuf;
    mid1D = roverMid;
    g.setBackground( bgColor );
    g.setComposite( acOpaque );
    g.clearRect( 0, roverTop, width, roverTop+vSize1D );
    g.setStroke( wideStroke );
    g.setColor( Color.GRAY );
    g.drawLine( 5, roverTop, width-5, roverTop );
    g.drawLine( width/2, roverTop-3, width/2, roverTop+3 );
    setSepStroke();

    int h = hitWhich( eyeI );
    if( h<54 ) {                                    // In a sticker.
      g.setColor( faceColor[ place[h].colorI ] );
      g.setComposite( acTransparent );
      g.fillRect( 0, mid1D-4, width, 8 );
      return;
    }

    /* Set the unit vectors for the various directions. */
    hf.setDir( roverHeading );
    lf.setDir( roverHeading - roverViewAngle/2. );
    rf.setDir( roverHeading + roverViewAngle/2. );
    hn.copy(hf).rot();
    ln.copy(lf).rot();
    rn.copy(rf).rot().rot().rot();

    StickerPlace p;
    V2F t = tF43;
    int i, j, k, n, nR=0, nv, l=-1, r=-1;
    for(i=0; i<width;  i++) {
      yBuf[i] = -999999.f;
      iBuf[i] = 54;
    }
    for(i=0; i<width;  i++) yBuf[i] = -999999.f;
    /* Compute squared distance of closest corner of each sticker to roving eyepoint.  Used by comparator.
       Copy sticker place's polygon points, relative to eyepoint, into the rp[] array in counterclockwise order.
       Determine whether any portion of sticker appears in rover view field, putting into placeR[] only
       references to those that show. */
    for(i=0; i<54; i++) {
      p = place[i];
      float dist, bestDist = 999999;
      boolean thereIsOutRight = false;
      boolean thereIsOutLeft  = false;
      boolean thereIsOutBoth  = false;
      boolean thereIsInside   = false;
      boolean thereIsInFront  = false;
      for(k=0; k<4; k++) {                          // Copy sticker corners as displacements from eyepoint.
        j = ( p.facesOut ? 3 - k : k );             // counterclockwise order
        t.copy( p.polyF[j] ).minus( eyeF ).flip();
        p.rp[k].copy(t);
        boolean outRight = ( rn.dot(t) < 0.f );
        boolean outLeft  = ( ln.dot(t) < 0.f );
        thereIsOutRight |= outRight;
        thereIsOutLeft  |= outLeft;
        thereIsOutBoth  |= ( outRight && outLeft );
        thereIsInside   |= !( outRight || outLeft );
        thereIsInFront  |= ( hf.dot(t) > 0.f );
        p.vis[k] = outRight ? ( outLeft ? OUT_BOTH : OUT_RIGHT ) : ( outLeft ? OUT_LEFT : INSIDE );
        dist = t.sqMag();
        if( dist < bestDist ) bestDist = dist;
      }
      p.roverDist = bestDist;
      if(   thereIsInside ||
          ( thereIsOutRight && thereIsOutLeft && thereIsInFront && !thereIsOutBoth ) ) placeR[ nR++ ] = p;
      /* There remains one extremely unusual case of an invisible sticker that can slip through;
         but that case will be caught later. */
    }
    Arrays.sort( placeR, 0, nR, roverCompare );     // Draw from front to back.

    /* Set viewDist2 so that the projected width of the view field is the frame width. */
    viewDist2 = .5f * width / (float)Math.tan( roverViewAngle/2. );

    /* Deal with polygon corners which lie outside view field.  The problem being addressed here is the fact
       that it is possible for some of a sticker to be visible while there exist non-visible corners which
       cannot be projected because they are even with or behind the eyepoint relative to the direction normal
       to the projection plane.  Such corners are moved onto the boundary of the view field, as this will not
       affect sticker's visible appearance, while making the point projectable.  The somewhat intensive
       computation here only arises in the relatively rare special cases when stickers do not lie entirely
       inside the view field; and, even then, most cases can still be dismissed easily. */
    for(i=0; i<nR; i++) {
      p = placeR[i];
      for(k=0; k<4; k++) {                      // Copy the current end points (Vertices) of the edges.
        v1[k].copy( p.rp[k]       );
        v2[k].copy( p.rp[(k+1)%4] );
      }
      nv=0;                                     // Counts Number of Visible edges.
      float pl = width, pr = 0.f;               // To be Positions of Leftmost and Rightmost vertices
      for(k=0; k<4; k++) {                      // Clip the edges to the view field.
        if( p.vis[k]         != INSIDE ) {
          if( p.vis[(k+1)%4] != INSIDE ) {      // Continue if there's a view field side both are on outside of.
            if( p.vis[k] == OUT_BOTH || p.vis[(k+1)%4] == OUT_BOTH )                       continue;
            if( p.vis[k] == p.vis[(k+1)%4] )                                               continue;
          }
          if(   p.vis[k] == OUT_BOTH ) {        // Try left side if right side does not work.
            if( !intersect( rf, v1[k], v2[k] ) ) if( !intersect( lf, v1[k], v2[k] ) )      continue;
          } else {
            if( !intersect( ( ( p.vis[k]       == OUT_LEFT ) ? lf : rf ), v1[k], v2[k] ) ) continue;
          }
        }
        if(   p.vis[(k+1)%4] != INSIDE ) {      // Other end point is no longer outside (if it ever was).
          if( p.vis[(k+1)%4] == OUT_BOTH ) {
            if( !intersect( rf, v2[k], v1[k] ) ) if( !intersect( lf, v2[k], v1[k] ) )      continue;
          } else {
            if( !intersect( ( ( p.vis[(k+1)%4] == OUT_LEFT ) ? lf : rf ), v2[k], v1[k] ) ) continue;
          }
        }
        project( v1[k] );
        project( v2[k] );
        if( v1[k].x<v2[k].x && v1[k].x<width && v2[k].x>=0 ) { // Edge is visible only if going left to right.
          ve[nv++] = k;                                        // nv will be Number of Visible edges
          if( v1[k].x < pl ) { l = k; pl = v1[k].x; }          // l and r will be the indices of edges with
          if( v2[k].x > pr ) { r = k; pr = v2[k].x; }          //     Leftmost and Rightmost vertices.
        }
      }
      if(nv==0) continue;                                      // Rare, but possible.

      /* Draw the visible edges of the sticker. */
      for(j=0; j<nv; j++) {                     // When nv==3 (rare), make k be remaining index besides l and r.
        k  = ve[j];
        if( k!=r && k!=l ) break;
      }
      Color bc = faceColor[  p.colorI ];        // Bright Color
      Color dc = dimColor1D[ p.colorI ];        // Dim    Color
      if(nv>0) drawEdge( p, (int)v1[r].x, v1[r].y,   (int)v2[r].x, v2[r].y, ( nv==1 ? bc : dc ) );
      if(nv>1) drawEdge( p, (int)v1[l].x, v1[l].y,   (int)v2[l].x, v2[l].y, ( nv==2 ? bc : dc ) );
      if(nv>2) drawEdge( p, (int)v1[k].x, v1[k].y,   (int)v2[k].x, v2[k].y,           bc        );
      drawSep(              (int)v1[l].x, v1[l].y  );
      drawSep(              (int)v2[r].x, v2[r].y  );

      if( p.highlight ) {
        g.setColor( Color.WHITE );
        g.setStroke( wideStroke );
        g.drawLine( (int)v1[l].x, mid1D-5, (int)v2[r].x, mid1D-5 );
        g.drawLine( (int)v1[l].x, mid1D+4, (int)v2[r].x, mid1D+4 );
        g.setStroke( sepStroke );
      }
    }

    if( labelsBox.isSelected() ) {
      for(i=0; i<6; i++) {
        p = place[9*i+4];
        t.setProj( tV31.setCurPos(9*i+4) ).times( scale ).flip().plus( p.center ).minus( eyeF ).flip();
        if( t.dot(ln) < 0.f  ||  t.dot(rn) < 0.f ) continue;  // It's out.
        project(t);
        g.setColor( faceColor[ p.colorI ] );
        g.drawChars( fChar, p.colorI, 1, (int)t.x - 5, ( p.facesOut ? mid1D-12 : mid1D+12 ) + 6 );
      }
    }
  }

  /* intersect(): Compute intersection of two lines.  One is a ray through origin in direction specified by
     unit vector h.  The other is the line passing through points p and q.  If distance of intersection
     from origin along direction h is positive, return true and replace coordinates of p with those of
     the intersection.  Otherwise, return false and do not change p. */
  boolean intersect( V2F h, V2F p, V2F q ) {
    V2F d = tF44.copy(p).minus(q).norm();       // direction from q to p
    float den = h.cross(d);                     // sin of angle between them
    float num = d.rot().dot(p);                 // distance from origin to the line
    if( Math.abs(den) < .0005f * Math.abs(num) ) return false;
    float rat = num/den;
    if( rat>0.f) p.copy(h).times(rat);
    return rat>0.f;
  }

  /* project(): Replace coordinates of argument with those of its projection. */
  void project( V2F p ) {
    float t = width/2 + (int)( viewDist2 * p.dot(hn) / p.dot(hf) );
    p.y = -p.mag();                             // For drawEdge, bigger means closer.
    p.x = t;
  }

  public final Comparator<StickerPlace> roverCompare = new Comparator<StickerPlace>() {
      public int compare( StickerPlace o1, StickerPlace o2 ) {
        float d1 = o1.roverDist;
        float d2 = o2.roverDist;
        return ( d1==d2 ? ( ((StickerPlace)o1).facesOut ? -1 : 1 ) : ( d1<d2 ? -1 : 1 ) );
      }
    };


/******** Mouse Handling ********/

  int     nDown = 0;                  // Tracks how many mouse buttons are down.
  long    downWhen;                   // time in ms when first mouse button went down
  V2I     downPlace    = new V2I();   // where first down occurred when a mouse button is in a depressed state
  V2I     downCenter;                 // reference to center (in_Center or outCenter) nearest downPlace
  boolean dragging     = false;       // true when dragging mouse for anything
  int     dragFor;                    // When dragging is true, the value of this indicates what dragging is for.
  V2I     iniDragWhat  = new V2I();   // when dragging a center, a copy of initial position of dragged center
  V2I     dragWhat;                   // when dragging, a reference to the center for the stickers being dragged
  V2I     downError    = new V2I();   // error of downPlace relative to center on drag
  boolean roverWaiting = false;       // true when buttons are down but one has not come up yet
  boolean downButtonIs1;              // for rover drag.  Specifies which button was pressed first.

  final int ORIENT          = 40;     // Possible values for dragFor.
  final int TUMBLE          = 41;     // ORIENT is undecided TUMBLE or ROTATE
  final int ROTATE          = 42;
  final int ANIMATE         = 43;
  final int MOVE_IN__CENTER = 45;     // Remainder used for undo/redo.
  final int MOVE_OUT_CENTER = 46;
  final int ROVER_DRAG      = 48;     // It is important that the four specific ones (not DRIVE) follow in order.
  final int ROVER_ADVANCE   = 48;
  final int ROVER_HEADING   = 49;
  final int ROVER_SLIDE     = 50;
  final int ROVER_ANGLE     = 51;
  final int ROVER_DRIVE     = 52;
  final int NOTHING         = -1;

  /* The mouse listener dispatches to the mouse handling routines defined here. */

  void downMouse( MouseEvent e ) {
    int i, j;
    if( ++nDown > 2 ) return;                              // 2 buttons were already down.
    if( nDown == 1  ) {
      downWhen = System.currentTimeMillis();
      downButtonIs1 = ( e.getButton() == MouseEvent.BUTTON1 );
      wasDriving = driving;
      if( downButtonIs1  &&  driving  &&  !e.isShiftDown() ) driveStop();
    }
    downPlace.copy( e.getPoint() );
    downCenter =                                           // Note closer center.
      ( downPlace.sqDist( outCenter ) < downPlace.sqDist( in_Center ) ) ? outCenter : in_Center;
    if( driving && downPlace.y<roverTop ) driveStop();
    driveX = downPlace.x;

    /* Deal with rover. */
    if( roverWaiting ) {
      if( nDown==2 ) return;                               // Still waiting for button up.
      roverWaiting = false;                                // Not supposed to happen.
    }
    if( downPlace.y > roverTop ) {
      if( nDown == 2  &&  !downButtonIs1 ) driveStop();
      if( nDown == 2 ) roverWaiting = true;
      if( !e.isControlDown()  &&  !e.isAltDown() ) return;
      dragFor = e.isControlDown() ? ( e.isShiftDown() ? ROVER_ADVANCE : ROVER_SLIDE   ) :
                                    ( e.isShiftDown() ? ROVER_ANGLE   : ROVER_HEADING );
      initRoverAdjust();
      return;
    }

    /* When a second mouse button goes down, turn it over to mouseDrag(). */
    if( nDown>1 ) {
      if( dragging ) return;                               // Does not mean anything if already dragging.
      dragging = true;
      dragFor  = ORIENT;                                   // undecided between tumbling and rotating
      dragMouse(e);                                        // Make the "+" now.
      return;
    }

    /* To move a center, must hold down Ctrl and either Alt or Meta.
       With above or if two buttons are down it is a drag for sure.
       Otherwise, anything less than dragThreshold pixel movement is regarded as a click. */
    if(  e.isControlDown() && ( e.isMetaDown() || e.isAltDown() ) ) {
      /* Figure out what center will be dragged if a center drag occurs.  */
      i = hitWhich( downPlace );
      if( i<54 ) {
        dragging = true;
        dragFor  = ( place[i].facesOut ? MOVE_OUT_CENTER : MOVE_IN__CENTER );
        dragWhat = place[i].center;
        iniDragWhat.copy( dragWhat );
        downError.copy( downPlace ).minus( dragWhat );
        /* Error corresponds to how far the mouse point missed the actual center. */
        if( whatsChanging != dragFor ) curStack.push( new MoveCenter( dragWhat ) );
        whatsChanging = dragFor;
      }
    }
  }

  V2F     iniEye = new V2F();   // saved version of eyeF for drag adjustment
  double  iniHeading;           // saved copy of rover heading for drag adjustment
  double  iniViewAngle;         // saved copy of rover view angle for drag adjustment

  void initRoverAdjust() {
    roverWaiting = false;
    dragging     = true;
    iniHeading   = roverHeading;
    iniViewAngle = roverViewAngle;
    iniEye.copy( eyeF );
    if( roverChanging ) return; // If arrow key adjustment in progress an undoing Act has already been pushed.
    if( whatsChanging != dragFor  &&  roverUndoableBox.isSelected()  ) curStack.push( new ChangeRover() );
    whatsChanging = dragFor;
    return;
  }

  V2I  tI61 = new V2I(), tI62 = new V2I(), tI63 = new V2I(), tI64 = new V2I();
  V3F  tV62 = new V3F(), tV63 = new V3F(), tV64 = new V3F();
  V2F  tF61 = new V2F(), tF62 = new V2F();

  void dragMouse( MouseEvent e ) {
    StickerPlace negFace, posFace;
    V2I u = tI62, v = tI63, p = tI61.copy( e.getPoint() );
    double d, newRot=0.;
    int i;
    V3F t  = tV64;

    /* Switch to drag-based animation if an animation is already in progress. */
    if( animating  &&  onClock  &&  twistQueue.n == 0 ) {
      onClock   = false;
      dragging  = true;
      dragFor   = ANIMATE;
      downAngle = aniAngle;                                // Note animation angle current at time of drag start.
      aniTimer.stop();
    }

    if(  p.x < 0  ||  p.x > width  ||  p.y < 0  ||  p.y > fullHeight  ) { driveStop(); } // Out of panel.
    if( !dragging && ( downPlace.distance(p) < dragThresholdP.val ) ) return; // Don't know what to do with it.
    if( !dragging ) downPlace.copy(p);                     // Eliminate drag threshold jerk.
    highlightsOff();                                       // We are dragging now.  Turn highlighting off.

    if( !animating  &&  twistQueue.n==0  &&  
        ( !dragging || dragFor==ORIENT ) ) {               // Decide on rotation or tumbling.
      dragging  = true;
      dragFor  = ( ( downPlace.distance( downCenter ) > .8f*pixRad ) ? ROTATE : TUMBLE );
      iniAzi = azi;  iniElev = elev;  iniRot = rot;        // Save initial orientation parameters.
      for(i=0; i<3; i++) iA[i].copy( oA[i] );              // both versions
      tumbleDisplace = 0.f;
      if( whatsChanging != dragFor  &&  viewUndoableBox.isSelected() ) curStack.push( new Orient() );
      whatsChanging = dragFor;
    }

    v.copy( p         ).minus( downCenter );               // v is vector to current drag place from center.
    u.copy( downPlace ).minus( downCenter );               // u is vector to original down place.

    /* Handle rover. */
    if( dragFor == ROVER_ADVANCE )
      moveRover( tF62.copy(iniEye).plus( tF61.setDir(roverHeading).times( p.x - downPlace.x ).flip()       ) );
    if( dragFor == ROVER_SLIDE   )
      moveRover( tF62.copy(iniEye).plus( tF61.setDir(roverHeading).times( p.x - downPlace.x ).flip().rot() ) );
    if( dragFor == ROVER_HEADING )
      modifyAngleP( roverHeadingP,   iniHeading   - ( p.x - downPlace.x ) * 3.49 / width );
    if( dragFor == ROVER_ANGLE   )
      modifyAngleP( roverViewAngleP, iniViewAngle - ( p.x - downPlace.x ) * PI   / width );

    /* Handle animation. */
    if( dragFor == ANIMATE  ) {                         // Update animation angle.
      negFace  = place[  4 + 18*( twistAxis ) ];        // negative facing face sticker for this axis
      posFace  = place[ 13 + 18*( twistAxis ) ];        // positive facing face sticker for this axis
      t = posFace.pos;                                  // Points in positive dir. of twist axis, magnitude 1.5.
      int outCount = 0;
      for(i=4; i<54; i+=9) if( place[i].facesOut ) outCount++;
      if( downPlace.y > Math.min( orthoTop, roverTop ) ) {
        d = 2.*PI * ( downPlace.x - p.x ) / width;
        if( twistAxis == 1 ) d = -d;                         // Not sure why!
      } else if( outCount==1  && (  posFace.facesOut  ||  negFace.facesOut  ) ) {
        d = Math.atan2( v.y, v.x ) - Math.atan2( u.y, u.x ); // Use angular position relative to center.
        if( negFace.facesOut ) d = -d;
      } else {                                               // Use motion perpendicular to twist axis.
        double angle = Math.atan2( t.yProj(), t.xProj() );
        d = (   Math.cos( angle ) * ( downPlace.y - p.y )
              + Math.sin( angle ) * ( downPlace.x - p.x ) ) * 2.*PI / pixRad;
      }
      aniGoTo( downAngle + ( ( dirControl ^ ( twistAxis == 1 ) ) ? -d : d ) );
    }

    /* Handle rotation. */
    if( dragFor == ROTATE )                             // Update viewing rotation angle.
      modifyAngleP( rotP, iniRot + Math.atan2( u.y, u.x ) - Math.atan2( v.y, v.x ) );

    /* Handle tumbling. */
    if( dragFor == TUMBLE ) {
      V3F a  = tumbleAxis; // for a unit vector in projection plane along Axis about which tilt is to occur
      V3F n  = tV62;       // for a unit vector Normal to above, also in projection plane
      V3F nn = tV63;       // for a New version of the above which is to be tilted into projection plane

      v.copy(p).minus( downPlace );                     // Now relative to initial down place.
      if( v.x==0 && v.y==0 ) {                          // Back to where we started.  Can't divide by vmag.
        elev = iniElev; azi = iniAzi; rot = iniRot;     // Restore initial orientation.
      } else {                                          // Compute new iO, jO, and kO.
        tumbleDisplace = v.mag();
        float  cost = v.x/tumbleDisplace;
        float  sint = v.y/tumbleDisplace;
        double ang  = tumbleDisplace * PI / pixRad;
        float  cosa = (float)Math.cos( ang );
        float  sina = (float)Math.sin( ang );

        n .copy( iI ).times( cost ).plus( t.copy( jI ).times( -sint ) );
        a .copy( jI ).times( cost ).plus( t.copy( iI ).times(  sint ) );
        nn.copy( n  ).times( cosa ).plus( t.copy( kI ).times(  sina ) );
        kO.copy( kI ).times( cosa ).plus( t.copy( n  ).times( -sina ) );
        iO.copy( nn ).times( cost ).plus( t.copy( a  ).times(  sint ) );
        jO.copy( kO ).cross( iO );

        /* Update orientation parameters - elevation, azimuth, and rotation. */
        if(  kO.v[0] == 0.f  &&  kO.v[1] == 0.f  ) {    // If pointed straight up, make azimuth 0.
          assert false;                                 // Don't expect to get here.  Just curious.
          elev = PI/2.;
          azi  = 0.;
          rot  = Math.atan2( iO.v[1],  iO.v[0] );
        } else {                                        // Otherwise, read new orientation angles.
          elev = Math.asin ( kO.v[2]           );
          azi  = Math.atan2( kO.v[0],  kO.v[1] );
          rot  = Math.atan2( iO.v[2], -jO.v[2] );
        }
      }
      setOrient( azi, elev, rot );                      // Register new orientation with GUI.
    }

    /* Handle center moving.  First check to see if we have dragged too far.  */
    if( dragFor == MOVE_OUT_CENTER  ||  dragFor == MOVE_IN__CENTER ) {
      u = p.minus( downError );                         // Position to which center has been dragged.
      u.x = Math.min(  width-40, Math.max( 40, u.x ) ); // Don't let it get too close to edge.
      u.y = Math.min( height-40, Math.max( 40, u.y ) );
      dragWhat.copy(u);                                 // Adjust outCenter or in_Center V2I object.
      if( lockCentersBox.isSelected() ) { in_Center.copy(u); outCenter.copy(u); }
    }
    pane.repaint();
  }

  /* Update orientation parameters. */
  void setOrient( double azi, double elev, double rot ) {
    modifyAngleP(  aziP, azi  );
    modifyAngleP(  rotP, rot  );
    modifyAngleP( elevP, elev );
  }

  V2I tI71 = new V2I(), tI72 = new V2I();
  V2F tF71 = new V2F(), tF72 = new V2F(), tF73 = new V2F();
  V3F tV71 = new V3F(), tV72 = new V3F();

  boolean wasDriving = false;   // Is true after initial button 1 down if driving at the time.

  void upMouse( MouseEvent e ) {

    V2I upPt = tI71.copy( e.getPoint() );
    boolean thisButtonIs1 = ( e.getButton() == MouseEvent.BUTTON1 );

    /* If there is an undecided rover pane drag, we can settle it now.   Note decrement of nDown. */
    if( --nDown>0  &&  roverWaiting  ) {                // The order of the tests is important.
      downPlace.copy( upPt );                           // Eliminate jerk.
      driveX = downPlace.x;
      roverWaiting = false;
      dragging     = true;
      dragFor = ( thisButtonIs1 ? ( downButtonIs1 ? ROVER_ANGLE : ROVER_HEADING ) :
                                  ( downButtonIs1 ? ROVER_SLIDE : ROVER_ADVANCE )   );
      initRoverAdjust();
      return;
    }

    if( nDown > 0 ) return;                             // Still at least one button down.

    /* No buttons down.  Finish up any dragging that was in progress. */
    if( dragging ) {
      if( dragFor == ANIMATE ) endTwist( aniAngle );
      dragging  = false;
      pane.repaint();
      return;
    }

    /* Eliminate ambiguous clicks. */
    if( ( System.currentTimeMillis() - downWhen ) > 500 ) return; // Took too long.  Do not regard as click.
    if( upPt.sqDist( downPlace ) > 200. )                 return; // Moved too far.  Do not regard as click.

    /* Check for a rover acceleration or stop click in rover 1D projection area. */
    if( downPlace.y > roverTop ) {
      if( driveModeBox.isSelected()  &&  !( thisButtonIs1  ^  e.isShiftDown() ) ) { driveAcc();  return; }
      if( wasDriving ) return;                          // Avoid starting a twist when just stopping drive.
    } // Still a possibility of a twist click with button 1 in rover 1D projection area.

    /* It's a click!  Figure out which face has been indicated by the click.  */

    V2F t = tF73;
    float dist, bestDist=999999.f;
    int i, j, iBest=0, iBest2=0, face;

    if( downPlace.y > roverTop ) {                      // Click in rover area.  Find nearest face sticker.
      for(i=4; i<54; i+=9) {
        t.setPlot( tV31.setCurPos(i), place[i].center ).minus( eyeI ).flip();
        if( t.dot(ln) < 0.f  ||  t.dot(rn) < 0.f ) continue;  // It's out.
        project(t);
        dist = Math.abs( t.x - downPlace.x );
        if( dist < bestDist ) { bestDist = dist; iBest = i; }
      }
    } else if( downPlace.y > orthoTop ) {               // Click in ortho area.  Find nearest face sticker.
      for(i=4; i<54; i+=9) {
        dist = Math.abs( t.setPlot( tV31.setCurPos(i), place[i].center ).x - downPlace.x );
        if( dist < bestDist ) { bestDist = dist; iBest = i; }
      }
    } else if( ( clickHit1 = hitWhich(upPt) ) != 54 ) { // Find index of any sticker clicked on.
      iBest = clickHit1;
    } else if(  upPt.x >= width-90  &&  upPt.y >= height-24  &&  faceTatBox.isSelected() ) { // 
      /* Clicked on one of BFUDLR characters lower right with FCS tattle . */
      i = ( upPt.x + 92 - width )/14;                   // index (in bChar array) of character clicked on
      if( i>5 ) i=5;
      for(j=4; j<54; j+=9) if( place[j].colorI == bFace[i] ) break; // Find where face bFace[i] is now.
      iBest = j;
    } else if(  upPt.x <= 88        &&  upPt.y >= height-24  &&  modelTatBox.isSelected() ) {  // MCS tattle
      iBest = 9*bFace[ ( upPt.x - 5 )/14 ];             // Use initial position of face bFace[i].
    } else {                                            // Find closest in- and out-facing face stickers.
      int  iBestOut     = 0,        iBest_In     = 0;
      V2F   bestOut     = tF71,      best_In     = tF72;
      float bestOutDist = 9999999.f, best_InDist = 9999999.f;
      for(i=4; i<54; i+=9) {
        dist = t.setPlot( tV71.setCurPos(i), place[i].center ).minus( upPt ).sqMag();
        if( place[i].facesOut ) { if( dist<bestOutDist ) { bestOutDist = dist; iBestOut = i; bestOut.copy(t); } }
        else                    { if( dist<best_InDist ) { best_InDist = dist; iBest_In = i; best_In.copy(t); } }
      }

      /* If the two centers coincide, favor an out-facing one if the click is above. */
      iBest = ( best_InDist < bestOutDist  ||  bestOutDist == best_InDist  &&  bestOut.y < 0 )  ||
              ( ( bestOut.dist(best_In) < scale * faceSize * ( 2.f + stickerSize ) / 1.5f )  &&
                ( bestOutDist != best_InDist )                                               &&
                ( t.copy( outCenter ).sqDist( in_Center ) < pixRad*pixRad )                  &&
                ( t.copy( upPt ).minus( outCenter ).mag() > .8f*pixRad    )                  &&
                ( t.copy( upPt ).minus( in_Center ).mag() > .8f*pixRad    ) ) ? iBest_In : iBestOut;
      /* If centers are close to one another and click is outside, consider only in-facing stickers */
    }
    mouseTwist( e, iBest/9, false );
  }

  /* Set variables for twist parameters based on event modifiers and invoke twist.  Used for FlatPane also. */
  void mouseTwist( MouseEvent e, int face, boolean force3D ) {

    if( twistQueue.n == Q_LEN ) return; // No room for more pending twists.
    Twist t = new Twist(-1);            // Default parameters of twist will be replaced.

    /* Intended code line 2nd below:  ( e.isAltDown() || e.isMetaDown() ) ? wholeCase :
       Unfortunately, on my computer e.isMetaDown() returns true for any right mouse button click
       (independent of what sort of mouse) so I cannot test with the correct code.  */
    t.go    = ( e.isAltDown() && e.isControlDown() ) ? externalCase :
                e.isAltDown()                        ? wholeCase    :
              ( e.isControlDown()                    ? centerCase   :
              ( (face%2 == 0)                        ? negCase      : posCase ) );
    t.dir   = ( e.isShiftDown() ^ ( e.getButton() == MouseEvent.BUTTON1 ) ^
                   ( face==0 || face==3 || face==4 )); // for 3D
    if( !force3D  &&  !place[ 9*face ].facesOut  &&  !twistDir3DBox.isSelected() ) t.dir = !t.dir;
    t.axis     = face/2;
    twistQueue.push(t);
    checkQueue();
  }


  /* Tend to highlighting when the mouse moves with no button down. */
  void moveMouse( MouseEvent e ) {
    int j;

    /* If, when at least one mouse button is depressed, you use the keyboard to cause the OS to change
       focus to the window of another application, release a button or two, and then return
       focus to the applet frame, there is a possiblity of confusing the mouse tracking code here.
       This function will not be called unless NO mouse buttons are currently depressed.
       The following code line is an attempt to deal with the confused situation somewhat gracefully.
       It should properly shut down any dragging that had been in progress. */
    while( nDown > 0 ) upMouse(e);
    Point p = e.getPoint();
    if( driving && p.y<roverTop ) driveStop();
    driveX = p.x;
    if( !highlightBox.isSelected() || animating || dragging ) return;
    j = ( p.y < height )                                  ? hitWhich(p)    :
        ( orthoOn && ( Math.abs( orthoMid - p.y ) < 5 ) ) ? orthoIBuf[p.x] :
        ( roverOn && ( Math.abs( roverMid - p.y ) < 5 ) ) ? roverIBuf[p.x] : 54;
    if( hoverLast == j ) return;                            // No change.
    hoverLast = j;
    paintPanes();                                           // Will change.
    highlightsOff();
    if( j == 54 ) return;                                   // Not hovering over any sticker.
    setHighlights( place[j].pos );
  }

  int   hoverLast = 54;   // Index of last sticker over which mouse was seen.  54, if was not over any.

  /* Find all the stickers on same cubie as sticker at position p and set highlight flags.  */
  void setHighlights( V3F p ) {
    int i;
    V3F v = tV72.copy(p);
    for(i=0; i<3; i++) v.v[i] = Math.round( .9f * v.v[i] ); // Make all coords be +1, -1, or 0.  I.e., cubie pos
    for(i=0; i<3; i++) {
      if( Math.abs( v.v[i] ) > .5f ) {                      // magnitude 1. coord: Has a sticker for axis.
        v.v[i] *= 1.5f;                                     // Make magnitude be 1.5.
        placeSI[ spatialIndex(v) ].highlight = true;   ;    // Highlight this one.
        v.v[i] /= 1.5f;                                     // Put it back to magnitude 1.
      }
    }
  }

  void highlightsOff() { int i; for(i=0; i<54; i++) place[i].highlight = false; }

  /* Return index of sticker in which point argument lies.  Prefer out-facing sticker if it hits two.
     Return 54 if point misses all stickers. */
  int hitWhich( Point p ) {
    int i, j;
    for(  i=0;   i<54; i++) if( place[i].polyI.contains(p) ) break;
    if( i<54  &&  !place[i].facesOut ) {                    // Keep looking.
      for(j=i+1; j<54; j++) if( place[j].polyI.contains(p) ) break;
      if( j<54 ) i=j;
    }
    return i;
  }

  void exitMouse( MouseEvent e ) {
    if( driving ) driveStop();
    highlightsOff();
    paintPanes();
  }

/******** Twist Implementation ********/

  /* twist( ActionEvent ): Call with keyboard input action event as argument.  Sets state for twist(). */
  void twist( ActionEvent e ) {
    int i, j, face;

    if( twistQueue.n == Q_LEN ) return;                        // No room for more pending twists.
    Twist t = new Twist(-1);                                   // Default parameters of twist will be replaced.
    char  c = e.getActionCommand().charAt(0);
    if( c<64  ) c += 64;                                       // Transform control character to alphabetic.
    if( c>'Z' ) c -= 32;                                       // Transform lower case to upper case.
    for(i=0; i<6; i++) if( c == fChar[i] ) break;              // Resulting bFace[i] is face index.
    for(j=4; j<54; j+=9) if( place[j].colorI == i ) break;     // Find where that face is now.
    face          = ( keyboardMCSBox.isSelected() ) ? bFace[i] : j/9;
    t.axis        = face/2;                                    // Set Twist Axis.
    int mods      = e.getModifiers();
    boolean shift = ( ( mods & ( ActionEvent.SHIFT_MASK                         ) ) != 0 );
    boolean alt   = ( ( mods & ( ActionEvent.ALT_MASK   | ActionEvent.META_MASK ) ) != 0 );
    boolean ctrl  = ( ( mods & ( ActionEvent.CTRL_MASK                          ) ) != 0 );
    t.dir         = shift ^ ( face==0 || face==3 || face==4 ); // Direction: 3D
    /* Reverse twist direction if in-facing and 3D twist direction is NOT selected. */
    if( !place[ 9*face ].facesOut  &&  !twistDir3DBox.isSelected() ) t.dir = !t.dir;
    t.go          = alt  ? ( ctrl ? externalCase : wholeCase                         ) : 
                           ( ctrl ? centerCase   : ( face%2==0 ? negCase : posCase ) );
    twistQueue.push(t);
    checkQueue();
  }

  ActStack twistQueue = new ActStack();                        // queue for pending twists

  void checkQueue() {
    if( animating ) return;                                    // We'll get back to it when animation finishes.
    twistStack = undoStack;
    while( twistQueue.n > 0 ) {
      Act act = twistQueue.dequeue();
      if( !act.somethingToDo() ) continue;
      actFromTwistQueue = true;
        act.doIt();
      actFromTwistQueue = false;
      if( animating ) break;                                   // A queued undo or redo need not be for a twist.
    }
  }

  /* State for twisting. */
  boolean pendingTwist      = false;  // true when twistQueue is not empty at end of an animation
  boolean animating         = false;  // true while an animation is in progress
  boolean onClock           = false;  // true while an animation is run by timer
  boolean actFromTwistQueue = false;  // true when executing an Act's doIt() if it came from twistQueue
  boolean reflect1          = false;  // true when next requested twist should be mirroring
  boolean reflect2          = false;  // true when next twist to be performed should be mirroring
  int     clickHit1         = 54;     // index of last sticker hit on a click.  54 if last no sticker for twist.
  int     clickHit2         = 54;     // index of sticker hit for next twist to-be-performed. 
  V3F     mirNorm = new V3F();        // when reflect2 is true, a unit vector normal to the mirror surface
  double  aniAngle;                   // current angle for the animation process
  double  downAngle;                  // value of aniAngle at time a mouse button is depressed
  float   sinAn = 0.f, cosAn = 1.f;   // sine and cosine of aniAngle
  long    startTime;                  // used with timer for animation
  int     twistAxis = 0;              // axis-index of the axis about which we are twisting
  int     oAx1 = 1, oAx2 = 2;         // the indices of the other two axes besides twistaxis.
  boolean dirControl;                 // This flag is manipulated to achieve correct direction of twist.
  boolean sliceGo[];                  // Element true if the corresponding slice (of those orthogonal
                                      //   to the twist axis) is eligible for twisting.

  final boolean centerCase[]   = { false, true,  false }; // Possible value arrays for sliceGo.
  final boolean wholeCase[]    = { true,  true,  true  };
  final boolean negCase[]      = { true,  false, false };
  final boolean posCase[]      = { false, false, true  };
  final boolean externalCase[] = { true,  false, true  };

  V3F baseVec[] = { new V3F(1.f, 0.f, 0.f), new V3F(0.f, 1.f, 0.f), new V3F(0.f, 0.f, 1.f) };

  /* twist(): Call with effective parameters in state variables:  twistAxis, dirControl, and sliceGo.
              Reason:  Event handling code must be able to access them.  */
  void twist() {

    twistTime = (int)( twistTimeP.val * 1000.f );
    aniStep   = (int)(   aniStepP.val * 1000.f );
    highlightsOff();

    if( reflect2 ) {
      mirNorm.zero().v[ twistAxis == 0  ?  1 : 0 ] = 1.f;
      if( clickHit2 != 54 ) {
        int j  = (clickHit2/9)*9 + 4;                 // index of face sticker in same face
        if( j !=  clickHit2 ) {
          mirNorm.copy( place[ clickHit2 ].pos ).minus( place[j].pos ).norm().cross( baseVec[ twistAxis ] );
        }
      }
    }

    /* If we are not animating, don't start the clock. */
    if(  !drawing  ||  twistTime <= aniStep  ) {
      endTwist( PI/2. );
      return;
    }

    /* Otherwise, set up for timer and start it. */
    startTime = System.currentTimeMillis() - aniStep; // Minus aniStep so first step does something.
    animating = true;
    onClock   = true;
    doSort    = true;
    aniNext.actionPerformed( dummyEvent );            // Take first step now.
    aniTimer.setDelay( aniStep );
    aniTimer.start();
  }

  /* Listen to timer events.  Note that we are not assuming that the time delay
     experienced between steps is actually aniStep. */
  AbstractAction aniNext = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) {
        if( pendingTwist ) {    // Waiting for next timer event after finishing preceding twist allows painting.
          pendingTwist = false;
          aniTimer.stop(); 
          checkQueue(); 
          return; 
        }
        if( !animating || !onClock ) return;          // Apparently an extraneous event can come in.
        double angle = PI/2. * (double)( System.currentTimeMillis() - startTime ) / twistTime;
        if( angle >= PI/2. ) {
          if( twistQueue.n == 0 ) aniTimer.stop();    // No more twists pending.
          else                    pendingTwist = true;
          endTwist( PI/2. );
        } else {
          aniGoTo( angle );
        }
      }
    };

  V3F tV81 = new V3F(), tV82 = new V3F(), tV83 = new V3F(), tV84 = new V3F(), tV85 = new V3F();

  /* Just to put in array p[], just below. */
  V3F aV0 = new V3F(), aV1 = new V3F(), aV2 = new V3F(), aV3 = new V3F();

  boolean doPrint = false;      // ???

  /* Achieve specified twist angle during animation.  Update polygon points (polyF) of all sticker places. */
  void aniGoTo( double angle ) {                     // Units of angle argument are radians.
    V3F v = tV81, u = tV82, t = tV83;                // All temporary vectors or points.
    V3F p[] = { aV0, aV1, aV2, aV3 };                // for transformed sticker corners
    int i, j;

    aniAngle = angle;                                // Save externally to allow for mouse taking over animation.
    cosAn    = (float)Math.cos( angle );
    sinAn    = (float)Math.sin( angle );
    if( dirControl ) sinAn = -sinAn;
    oAx1  = ( twistAxis == 0 ? 1 : 0 );              // other two axis-indices besides twist axis:
    oAx2  = ( twistAxis == 2 ? 1 : 2 );              //      Note oAx1 < oAx2.

    for(i=0; i<54; i++) {                                        // Update polygon corners.
      if( !sliceGo[ place[i].slice[ twistAxis ] ] ) continue;    // Slice is not being turned.
      for(j=0; j<4; j++) p[j].copy( place[i].corner[j] ).turn();
      doPrint = false;
      t.copy( p[1] ).minus( p[0] );                             // 3D vector for 1st edge of sticker
      u.copy( p[2] ).minus( p[1] );                             // 2nd edge.  t x u is perpendicular to sticker
      v.copy( p[0] ).plus(  p[3] ).times( .5f ).minus( eye3D ); // vector from eye-position to this sticker
      /* Check dir. of t,u cross product relative to viewing vector to see which side of sticker is visible. */
      boolean facesOut = ( t.cross(u).dot(v) > 0.f ) ^ ( reflect2  &&  sinAn*sinAn > .5 );
      doSort |= ( facesOut != place[i].facesOut );              // If the face flipped, enable new sort.
      place[i].facesOut =   facesOut;
      place[i].center   = ( facesOut ? outCenter : in_Center );
      for(j=0; j<4; j++) place[i].polyF[j].setProj( p[j] ).flip().times( scale ).plus( place[i].center );
      pane.repaint();
    }
  }

  /* Twist finalization. */
  void endTwist( double angle ) {
    int i;
    /* Number of Right angles in the final animation angle rounded to a multiple of 90 degrees. */
    int nr =  ((int)Math.round( angle*2./PI ) + 20 ) % 4;  // Effective angle is nr*PI/2.
    cosAn  = (float)Math.cos( nr * PI/2. );                // Expensive way to compute 1s and 0s.  :)
    sinAn  = (float)Math.sin( nr * PI/2. );
    if( dirControl ) sinAn = -sinAn;
    if( drawing  &&  nr!=0 ) {
      twistStack.push( new Twist(nr) );                    // for undo: a Twist that will reverse this one.
      if( nr==2 ) twistStack.push( new Twist(nr) );        // Treat 180 as two twists.
    }

    /* Discover to which new place each sticker is being transported.  We can get here when not animating,
       but setCurPos() method needs animating true and the oAxi's in order to compute the final move. */
    animating = true;
    oAx1  = ( twistAxis == 0 ? 1 : 0 );                    // other two axis-indices besides twist axis:
    oAx2  = ( twistAxis == 2 ? 1 : 2 );
    for(i=0; i<54; i++) placeSI[ spatialIndex( tV84.setCurPos(i) ) ].newCol = place[i].colorI;
    for(i=0; i<54; i++) place[i].colorI = place[i].newCol; // Update color info for moved stickers
    animating = false;
    reflect2  = false;
    if( drawing ) paintPanes();
  }


/******** Roving Eye Adjustment by Arrow Keys ********/

  /* Rover state used in routines below. */
  float   roverSpeed;           // pixels/sec or radians/sec
  int     roverK;               // the "k" argument passed to roverKey so roverStep can know
  boolean roverChanging;        // true while arrow-directed timer-based adjustment is in progress
  boolean roverMove;            // true if Control modifier was used
  int     roverType;            // one of ROVER_SLIDE, ROVER_ADVANCE, ROVER_ANGLE, or ROVER_HEADING
  int     roverWrap;            // Used to prevent heading modification from continuing indefinitely.

  /* roverKey is called when an arrow key is hit with the Control or Alt modifier. */
  void roverKey( int k,         // Indicates which key: 0-left, 1-right, 2-up, 3-down
                 int mods       // modifier mask from ActionEvent
                 ) {
    if( roverFieldBox.isSelected() && !roverChanging ) {      // Need to start.
      roverMove = ( ( mods & ActionEvent.CTRL_MASK ) != 0 );
      roverType = roverMove ? ( k<2 ? ROVER_SLIDE : ROVER_ADVANCE ) : ( k<2 ? ROVER_ANGLE : ROVER_HEADING );
      /* If drag or driving adjustment in progress an undoing Act has already been pushed. */
      if( !( dragging  &&  ( dragFor - ROVER_DRAG < 4 ) ) &&  !driving  &&  roverUndoableBox.isSelected() ) {
        if( whatsChanging != roverType ) curStack.push( new ChangeRover() );
        whatsChanging = roverType;
      }
      rovStep = (int)( rovStepP.val * 1000.f );
      roverTimer.setDelay( rovStep );
      roverTimer.start();
      roverSpeed = ( roverMove ? 2.f : .06f );                // pixels/sec or radians/sec
      roverK = k;
      roverWrap = 0;
      roverChanging = true;
      roverStep.actionPerformed( dummyEvent );                // Get started right away.
      return;
    }

    roverSpeed *= 3.f;
    /* Once started in one direction, take any other arrow or a change of mods as a stop. */
    if( k == roverK  &&  roverMove == ( ( mods & ActionEvent.CTRL_MASK ) != 0 ) ) return;
    roverTimer.stop();
    roverChanging = false;
  }

  V2F tF81 = new V2F(), tF82 = new V2F();

  /* Listen to roverTimer events.  */
  AbstractAction roverStep = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) {
        float change = roverSpeed * rovStep/1000.f;
        if( roverK%2 != 0 ) change = -change;
        if( roverMove ) {
          if( roverK > 1 ) {    // forwards or backwards
            moveRover( tF81.copy( eyeF ).plus( tF82.setDir( roverHeading ).times( change ).flip()       ) );
          } else {              // left or right
            moveRover( tF81.copy( eyeF ).plus( tF82.setDir( roverHeading ).times( change ).flip().rot() ) );
          }
          if( roverOut ) roverKey( -1, 0 );  // This will stop the adjustment.
        } else {                // Adjusting heading or view angle.
          if( roverK > 1 ) {
            modifyAngleP( roverViewAngleP, Math.min( 2.97, Math.max( .1745, roverViewAngle - change ) ) );
          } else {
            double newAngle = roverHeading - change;
            if( Math.abs( newAngle ) > 2.*PI ) roverWrap++;
            if( roverWrap > 4 ) roverKey( -1, 0 );
            modifyAngleP( roverHeadingP, newAngle );
          }
        }
        pane.repaint();
      }
    };

  boolean roverOut=false;       // Set to true by moveRover when given point is found to be out.

  /* Move roving eye to specified position in screen coordinates. */
  void moveRover( V2F p ) {
    roverOut = ( p.x<0.f || p.y<0.f || p.x>width || p.y>height );
    p.x = Math.min( (float)width,  Math.max( 0.f, p.x ) );
    p.y = Math.min( (float)height, Math.max( 0.f, p.y ) );
    eyeI.copy( eyeF.copy(p) );
    modifyParam( roverXP, p.x/width        );
    modifyParam( roverYP, 1.f - p.y/height );
  }

/******** Drive Mode of Roving Eye Adjustment ********/

  V2F tF91 = new V2F(), tF92 = new V2F();
  V2I tI91 = new V2I();

  boolean driving;              // true when rover is moving in drive mode
  int     driveX;               // current x-position of the mouse. Maintained by mouse event handlers (not drag)
  float   driveSpeed = 0.f;     // pixels per second

  /* Accelerate the rover. */
  void driveAcc() {
    if( !driveModeBox.isSelected() ) return;
    if( driving ) {
      driveSpeed *= 2.f;
      return;
    } else {                                                   // Need to start.
      if( !roverChanging  &&  roverUndoableBox.isSelected() ) {
        if( whatsChanging != ROVER_DRIVE ) curStack.push( new ChangeRover() );
        whatsChanging = ROVER_DRIVE;
      }
      driveSpeed = 2.f;
      driveTimer.setDelay( rovStep );
      driveTimer.start();
      driving = true;
      driveStep.actionPerformed( dummyEvent );                 // Get started right away.
    }
  }

  /* Stop the rover. */
  void driveStop() { driving = false; driveTimer.stop(); }

  /* Listen to driveTimer events.  */
  AbstractAction driveStep = new AbstractAction() {
      public void actionPerformed( ActionEvent e ) {
        float step = driveSpeed * rovStep/1000.f;               // in pixels
        double hc  = ( driveX - width/2 ) * .0001 * driveSpeed; // heading change
        moveRover( tF92.copy( eyeF ).plus( tF91.setDir( roverHeading + hc/2. ).times( step ).flip() ) );
        if( roverOut ) driveStop();
        modifyAngleP( roverHeadingP, roverHeading + hc );
        pane.repaint();
      }
    };


/*********** Undo/Redo Capability and Queued-Twist Support **************/

  /* whatsChanging is a flag to prevent multiple consecutive acts from adjusting the same thing.
     If the last thing for which an Act was pushed was a parameter, it is the parameter index.
     It can also use the dragFor options (which is why they start at 40).  */
  int whatsChanging = NOTHING;

  /* Act is an abstract class for objects implementing doIt().  The classes which extend Act all
     create acts which will restore the status quo as of when the object was created. 
     Only Undo and Redo override somethingToDo(), returning false if corresponding stack is empty. */
  abstract class Act {
    Act() {};
    public void    doIt()          {}
    public boolean somethingToDo() { return true; }
  }

  class Twist extends Act {
    int     axis;
    boolean dir;
    boolean go[];
    boolean ref;
    int     ch;

    Twist( int nr ) {                    // Argument is [twist angle]/[right angle] modulo 4.  See endTwist();
      axis      = twistAxis;
      dir       = (nr==3) ^ !dirControl; // Sets direction for undoing twist.
      go        = sliceGo;
      ref       = nr<0 ? reflect1  : reflect2;
      ch        = nr<0 ? clickHit1 : clickHit2;
      reflect1  = false;                 // All twists are queued in twistQueue.  So reset here works for all.
      clickHit1 = 54;
    };

    public void doIt() {
      twistAxis  = axis;
      dirControl = dir;
      sliceGo    = go;
      reflect2   = ref;
      clickHit2  = ch;
      twistStack = curStack;             // Make copy of curStack for twist(), as undo() will reset immediately.
      if( actFromTwistQueue || animateUndoBox.isSelected() ) twist();
      else endTwist( PI/2. );
      /* In either case, endTwist() will push the undoing (or redoing) twist for THIS twist. */
    }
  }

  class Undo extends Act {
    public void    doIt()          { undo.actionPerformed( dummyEvent ); } 
    public boolean somethingToDo() { return undoStack.n>0; }
  }

  class Redo extends Act {
    public void    doIt()          { redo.actionPerformed( dummyEvent ); } 
    public boolean somethingToDo() { return redoStack.n>0; }
  }

  class Orient extends Act {
    double az, el, ro;
    Orient() { az=azi; el=elev; ro=rot; };
    public void doIt() {
      if( !viewUndoableBox.isSelected() ) { // Try next act on other stack (the one this one came from).
        if( curStack == undoStack ) redo.actionPerformed( dummyEvent );
        else                        undo.actionPerformed( dummyEvent );
        return;
      }
      curStack.push( new Orient() );
      setOrient( az, el, ro );
    }
  }

  class MoveCenter extends Act {
    V2I center;
    int x, y;
    MoveCenter( V2I c ) { center=c; x=c.x; y=c.y; };
    public void doIt() {
      curStack.push( new MoveCenter( center ) );
      center.set(x,y);
      if( lockCentersBox.isSelected() ) { in_Center.set(x,y); outCenter.set(x,y); }
    }
  }

  class ChangeParam extends Act {
    int i;
    float v;
    ChangeParam() { i=curParam; v=param[i].val; };
    public void doIt() {
      curParam = i;                // This side effect is probably desirable.  It could be avoided.
      curStack.push( new ChangeParam() );
      param[i].val = v;
      /* Select, for display in GUI, the parameter whose value has been updated. */
      param[i].actionPerformed( dummyEvent );
    }
  }

  class ChangeRover extends Act {
    float x, y;
    double hd, va;
    ChangeRover() { x=roverX; y=roverY; hd=roverHeading; va=roverViewAngle; }
    public void doIt() {
      if( !roverUndoableBox.isSelected() ) { // Try next act on other stack (the one this one came from).
        if( curStack == undoStack ) redo.actionPerformed( dummyEvent );
        else                        undo.actionPerformed( dummyEvent );
        return;
      }
      curStack.push( new ChangeRover()    );
      modifyParam(   roverXP,          x  );
      modifyParam(   roverYP,          y  );
      modifyAngleP(  roverViewAngleP,  va );
      modifyAngleP(  roverHeadingP,    hd );
    }
  }

  class Permute extends Act {
    int c[];                      // array for Color indices
    Permute() {
      int i;
      c = new int[54];
      for(i=0; i<54; i++) c[i] = place[i].colorI;
    }
    public void doIt() {
      int i;
      curStack.push( new Permute() );
      for(i=0; i<54; i++) place[i].colorI = c[i];
    }
  }

  /* Stack for Act objects. */
  final int Q_LEN = 100;

  class ActStack {
    Act q[];                                       // the queue which implements the stack
    int n;                                         // number of elements on stack
    int i;                                         // index of current top element
    int o;                                         // index of current oldest element

    ActStack() { q = new Act[Q_LEN]; };

    public void push( Act act ) {
      i    = (i+1) % Q_LEN;
      q[i] = act;
      if( n < Q_LEN ) ++n; else o = (o+1) % Q_LEN; // If stack is full, toss oldest entry.
      whatsChanging = NOTHING;                     // Any new act (to either stack) cancels last changing aspect.
    }

    public Act  pop() {                            // Return most recently pushed Act on stack.
      Act act = q[i];
      i = ( i - 1 + Q_LEN ) % Q_LEN;
      n--;
      return act;
    }

    public Act dequeue() {                         // Return least recently pushed Act on stack.
      o = (o+1) % Q_LEN;
      n--;
      return q[o];
    } 
  }

  ActStack undoStack   = new ActStack();
  ActStack redoStack   = new ActStack();
  ActStack trashStack  = new ActStack(); // current stack for redo when doing cancel of an undo.  Not looked at.
  ActStack curStack    = undoStack;      // current stack for pushing Acts.    It is redoStack when doing undo.
  ActStack twistStack  = undoStack;      // current stack for pushing twists.  It is redoStack when doing undo.

  /* Also see AbstractActions undo, redo, and cancel (undo) after class MainPane
     and various other occurrences of "curStack.push". */


/******** Layered (Flat) View Frame and Pane **************/

  JFrame   flatFrame;
  FlatPane flatPane;

  void makeFlatFrame() {
    flatPane  = new FlatPane();
    flatFrame = new JFrame("MC3D Layered View");
    flatFrame.addWindowListener( new FlatCloser() );
    flatFrame.getRootPane().setContentPane( (Container)flatPane );
    flatFrame.setSize( 408, 658 );
    Point p = frame.getLocationOnScreen();
    flatFrame.setLocation( p.x + frame.getWidth() + 5, p.y );
    flatFrame.setVisible(true);
  }

  class FlatCloser extends WindowAdapter {
    public void windowClosing( WindowEvent e ) { layeredBox.setSelected( false ); }
  }

  /* paintPanes() is called in situations in which the flatPane may need to be repainted.
     When only the main pane could need repainting, pane.repaint() only is called. */
  void paintPanes() {
    pane.repaint();
    if( layeredBox.isSelected() ) flatPane.repaint();
  }

  Color cellBackground = new Color( 208, 208, 196 );

  class FlatPane extends JPanel implements MouseListener, MouseMotionListener  {

    FlatPane() {
      super();
      addMouseListener(this);
      addMouseMotionListener(this);
      keyBoardInSetup( getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW ), getActionMap() );
    }

    V3F cl = new V3F();   // Holds cubie location with y-coord negated.  Used for directions relative to cell.
    V3F t  = new V3F();   // Used for 2D.  The z-component is ignored.  Used to allow indexing coordinates.
    V3F s  = new V3F();   // Similar to t, for text side.
    Polygon polygon = new Polygon();
    int width, height, eHeight;
    int cellSep, boardSep, nColors, zAx;
    int cs1, cs2, cs3, cs6;

    public void paintComponent( Graphics g1 ) {
      super.paintComponent(g);
      Graphics2D g = (Graphics2D)g1;
      int        i, j, k;

      width    = (flatPane.getWidth()/4)*4;   // to assure consistent placement make be divisible by 4
      height   = (flatPane.getHeight()/2)*2;
      eHeight  = (int)( .9f * Math.min( height, width+width/2 ) );
      cellSep  = (int)(eHeight/10.5f);        // cell separation
      boardSep = (int)(eHeight/3.f);          // board separation
      cs1      = ((int)(eHeight/11.25f)/6)*6; // Cell Size - forced to be multiple of 6
      cs2      = cs1/2;                       // sub-multiples of cell size
      cs3      = cs1/3;
      cs6      = cs1/6;
      zAx      = 0;                           // <2 axis for which coordinate is Zero (if any)

      g.setBackground( bgColor );
      g.setComposite( acOpaque );
      g.clearRect( 0, 0, width, height );
      g.setFont(font);
      g.setStroke( wideStroke );

      /* Make text cells all off white. */
      g.setColor( cellBackground );
      for(i=-1; i<2; i++) for(j=-1; j<2; j++) for(k=-1; k<2; k++ )
        g.fillRect( (int)( 3*width/4 + i*cellSep - cs2 ),
                    (int)(  height/2 + j*cellSep + k*boardSep - cs2 ),
                    cs1, cs1 );

      /* Make text face-sticker cells be dim face color. */
      for(i=0; i<6; i++) {
        g.setColor( dimColor1D[ place[9*i+4].colorI ] );
        setCellLoc( t, 9*i+4 );
        g.fillRect( (int)( t.v[0] + width/2 -cs2 ), (int)( t.v[1] - cs2 ), cs1, cs1 );
      }

      /* Draw each sticker in both presentations. */
      for(i=0; i<54; i++) {
        StickerPlace p = place[i];
        setCellLoc( t, i );                     // Start t at center of cell for this sticker.  Also sets cl.
        s.copy(t).v[0] += width/2;              // s at center of text cell.
        int axis = p.axis;
        int oAx  = ( axis == 0 ? 1 : 0 );       // when axis<2, other such axis
        boolean zCenter = false;
        for(j=0, nColors=0; j<3; j++) {
          if( Math.abs( cl.v[j] ) > .1f ) nColors++;
          else {                                // Cubie is in a center slice for axis j.
            if(j<2) zAx = j;
            zCenter = (j==2);                   // ultimately true if cubie is in z-center-slice
          }
        }
        int nzAx = 1 - zAx;                     // Non-Zero Axis:  <2 axis besides zAx

        /* Compute corners (in temp variable t) of polygon and add them to polygon object. */
        polygon.reset();
        if( nColors == 1 ) {                                 // On a face cubie.  Whole cell.
          t.v[0]      += cs2;                                   // Move to a corner.
          t.v[1]      += cs2;                    add(t);
          t.v[0]      -= cs1;                    add(t);
          t.v[1]      -= cs1;                    add(t);
          t.v[0]      += cs1;                    add(t);
        } else if( nColors == 2  &&  zCenter ) {             // On an edge cubie in center slice.  Triangle.
          t.v[0]      += cs2 * cl.v[0];
          t.v[1]      += cs2 * cl.v[1];          add(t);
          t.v[oAx]    -= cs1 * cl.v[oAx];        add(t);
          t.v[axis]   -= cs1 * cl.v[axis];       add(t);
        } else if( nColors == 2 )  {                         // On an edge cubie in U or D slice.  Rectangle
          float hc2 = ( axis==2 ? -cs2 : cs2 );                 // Determines inner or outer rectangle.
          t.v[zAx]    += cs2;                    add(t);
          t.v[zAx]    -= cs1;                    add(t);
          t.v[nzAx]   += hc2 * cl.v[nzAx];       add(t);
          t.v[zAx]    += cs1;                    add(t);
        } else {                                             // On a corner cubie.
          add(t);                                               // Center of cell is a corner.
          if( p.axis == 2 ) {                                   // up- or down-facing sticker.  Little square.
            t.v[0]    -= cs2 * cl.v[0];          add(t);
            t.v[1]    -= cs2 * cl.v[1];          add(t);
            t.v[0]    += cs2 * cl.v[0];          add(t);
          } else {                                              // lateral-facing sticker.  Trapezoid.
            t.v[oAx]  -= cs2 * cl.v[oAx];        add(t);
            t.v[axis] += cs2 * cl.v[axis];       add(t);
            t.v[oAx]  += cs1 * cl.v[oAx];        add(t);
          }
        }

        /* Draw the polygon. */
        g.setColor( faceColor[ p.colorI ] );
        g.fillPolygon( polygon );

        /* Do a rectangle for sticker-name/facing-direction pair for text cell. */
        t.copy( p.pos );
        for(j=0; j<3; j++) if( j != axis ) t.v[j] = 0.f; // Make t be position of face sticker on same face.
        int face = placeSI[ spatialIndex(t) ].colorI;    // face in FCS sense. (ColorI is face-index on init.)
        if( textDirMCSBox.isSelected() ) face = p.face;
        int scLeft = (int)s.v[0] + ( p.colorI/2 - 1 ) * cs3 - cs6; // Sub-Cell left position
        g.setColor( faceColor[ p.colorI ] );
        g.fillRect( scLeft, (int)s.v[1] - cs2, cs3, cs2 );
        g.setColor( labelColor[ p.colorI ] );
        g.drawChars( fChar, p.colorI, 1, scLeft + cs6 - 5, (int)s.v[1] - cs2/2 + 6 );
        g.setColor( faceColor[face] );
        g.fillRect( scLeft, (int)s.v[1],       cs3, cs2 );
        g.setColor( labelColor[face] );
        g.drawChars( lChar, face,     1, scLeft + cs6 - 4, (int)s.v[1] + cs2/2 + 6 );

        if( p.highlight ) {
          g.setColor( Color.WHITE );
          for(j=0; j<2; j++) {
            t.copy(s);
            polygon.reset();
            t.v[0] -= cs2+1 + j*width/2;;
            t.v[1] -= cs2+1;  add(t);
            t.v[0] += cs1+2;  add(t);
            t.v[1] += cs1+2;  add(t);
            t.v[0] -= cs1+2;  add(t);
            g.drawPolygon( polygon );
          }
        }
      }

      if( labelsBox.isSelected() ) {
        for(j=0; j<6; j++) {
          setCellLoc( t, 9*j+4 );
          g.setColor( labelColor[ place[9*j+4].colorI ] );
          g.drawChars(   fChar,   place[9*j+4].colorI, 1, (int)(t.v[0]-5), (int)(t.v[1]+6) );
        }
      }
    }

    /* Set x and y coordinates of result to location of center of cell for corresponding cubie.
       Leave point cl as Location of center of Cubie with y-coordinate reversed. */
    void setCellLoc( V3F result, int placeIndex ) {
      int j;
      cl.copy( place[ placeIndex ].pos );
      cl.v[1] = -cl.v[1];
      for(j=0; j<3; j++) cl.v[j] = Math.round( .9f * cl.v[j] );
      result.set( width/4  + cl.v[0] * cellSep,
                  height/2 + cl.v[1] * cellSep - cl.v[2] * boardSep, 0.f );
    }

    void add( V3F p ) { polygon.addPoint( (int)p.v[0], (int)p.v[1] ); }

    V3F tV1 = new V3F(), tV2 = new V3F();
    V2F tF2 = new V2F();

    public void mouseClicked( MouseEvent e ) {
      float dist, bestDist = 999999.f;
      int j, jBest=0;
      Point p = e.getPoint();
      for(j=0; j<12; j++) {     // Loop on faces.  Second pass is for text layout on right.
        setCellLoc( t, 9*(j%6)+4 );
        dist = tF2.set( t.v[0] + ( j>5 ? width/2 : 0 ), t.v[1] ).sqDist(p);
        if( dist < bestDist ) { bestDist = dist; jBest = j%6; }
      }
      mouseTwist( e, jBest, true );
    }

    public void mouseMoved( MouseEvent e ) {
      Point p = e.getPoint();
      int j;
      for(j=0; j<108; j++) {
        setCellLoc( t, j%54 );
        if( Math.abs(t.v[0] + (j>53 ? width/2 : 0) - p.x) <= cs2  &&  Math.abs(t.v[1]-p.y) <= cs2 ) break;
      }
      j = (j==108) ? 54 : j%54;
      if( j == hoverLast ) return;
      hoverLast = j;
      highlightsOff();
      paintPanes();
      if(j==54) return;
      setHighlights( place[j].pos );
    }

    public void mouseExited  ( MouseEvent e ) {
      highlightsOff();
      paintPanes();
    }

    public void mouseEntered ( MouseEvent e ) { }
    public void mousePressed ( MouseEvent e ) { }
    public void mouseReleased( MouseEvent e ) { }
    public void mouseDragged ( MouseEvent e ) { }
  }

/******** Vector and Point Utilities for 2D and 3D ********/

  /* Class V3F: 3-space vector object.  Also used as a 3-D point, regarding vector as displacement
     of point from origin.  Having the coordinates in an array allows axis index to be treated as data,
     a possibility missing in the vecmath package.  The class is more interesting than just a
     float[3] because of the various methods, some of which are specific to this application.  */

  V3F tV  = new V3F();                     // temp V3F used below (only) by V3F

  class V3F extends Object {
    float v[];

    V3F( float x, float y, float z ) {
      v = new float[3];
      v[0] = x;
      v[1] = y;
      v[2] = z;
    }

    V3F( float u[] ) {
      v = new float[3];
      v[0] = u[0];
      v[1] = u[1];
      v[2] = u[2];
    }

    V3F( V3F u ) {
      v = new float[3];
      v[0] = u.v[0];
      v[1] = u.v[1];
      v[2] = u.v[2];
    }

    V3F() {
      v = new float[3];
      v[0] = 0.f;
      v[1] = 0.f;
      v[2] = 0.f;
    }

    V3F zero() {                           // Set all the coordinates of this to zero.
      v[0] = 0.f;
      v[1] = 0.f;
      v[2] = 0.f;
      return this;
    }

    V3F set( float x, float y, float z ) { // Set the coordinates of this to specified x, y, and z.
      v[0] = x;
      v[1] = y;
      v[2] = z;
      return this;
    }

    V3F copy( V3F u ) {                    // Copy coordinates of u into this.
      v[0] = u.v[0];
      v[1] = u.v[1];
      v[2] = u.v[2];
      return this;
    }

    V3F plus( V3F u ) {                    // Add u to this.
      v[0] += u.v[0];
      v[1] += u.v[1];
      v[2] += u.v[2];
      return this;
    }

    V3F minus( V3F u ) {                   // Subtract u from this.
      v[0] -= u.v[0];
      v[1] -= u.v[1];
      v[2] -= u.v[2];
      return this;
    }

    V3F times( float factor ) {            // Multiply all coordinates of this by factor.
      v[0] *= factor;
      v[1] *= factor;
      v[2] *= factor;
      return this;
    }

    V3F cross( V3F u ) {                   // Return cross product of this and u.
      float s = v[1]*u.v[2] - v[2]*u.v[1];
      float t = v[2]*u.v[0] - v[0]*u.v[2];
      v[2]    = v[0]*u.v[1] - v[1]*u.v[0];
      v[0]    = s;
      v[1]    = t;
      return this;
    }

    float dot( V3F u ) {                   // Return dot product of this and u.
      return v[0]*u.v[0] + v[1]*u.v[1] + v[2]*u.v[2];
    }

    float sqMag() {                        // Return the squared magnitude of this.
      return v[0]*v[0] + v[1]*v[1] + v[2]*v[2];
    }

    float mag() {                          // Return the magnitude of this.
      return (float)Math.sqrt( (double)( v[0]*v[0] + v[1]*v[1] + v[2]*v[2] ) );
    }

    float sqDist( V3F u ) {                // Return squared distance between u and this.
      return tV.copy(this).minus(u).sqMag();
    }

    float dist( V3F u ) {                  // Return distance between u and this.
      return tV.copy(this).minus(u).mag();
    }

    V3F norm() {                           // Return a unit vector pointing in same direction as this.
      return this.times( 1.f/this.mag() );
    }

    V3F turn() {                           // Transform this by the current animation transformation.
      if( reflect2 ) {
        return this.minus( tV.copy( mirNorm ).times( this.dot( mirNorm ) * (float)(2.*sinAn*sinAn) ) );
      } else {
        float t = cosAn*v[oAx1] + sinAn*v[oAx2];
        v[oAx2] = cosAn*v[oAx2] - sinAn*v[oAx1];
        v[oAx1] = t;
        return this;
      }
    }

    float xProj() {                        // Return x-coordinate of projection of this onto projection plane
      return iO.dot( tV.copy( this ).minus( eye3D ) ) / ( 1.f - kOverViewDist3.dot( this ) );
    }

    float yProj() {                        // Return y-coordinate of projection of this onto projection plane
      return jO.dot( tV.copy( this ).minus( eye3D ) ) / ( 1.f - kOverViewDist3.dot( this ) );
    }

    float xP( V2I center ) {               // Return x-pixel-coordinate for where the projection of this will
                                           //   draw when plotted relative to argument center.
      return ( center.x + (int)( scale * this.xProj() ) );
    }

    float yP( V2I center ) {               // Return y-pixel-coordinate for where the projection of this will
                                           //   draw when plotted relative to argument center.
      return ( center.y - (int)( scale * this.yProj() ) );
    }

    V3F setCurPos( int k ) {               // Set this to the current center position of the place[k] sticker.
      this.copy( place[k].pos );
      if( animating  &&  sliceGo[ place[k].slice[ twistAxis ] ] ) this.turn();
      return this;
    }

    public String toString() {
      String s = new String("(");
      return s.concat( Float.toString( v[0] ) ).concat(", ")
              .concat( Float.toString( v[1] ) ).concat(", ")
              .concat( Float.toString( v[2] ) ).concat(") ");
    }
  }

  /* Class V2F: 2-space Vector object with Floats for coordinates.  Also used as a 2D point, regarding vector
     as displacement of point from origin.  Defines vector calculus functions similar to those of V3F. */

  V2F tV2 = new V2F();          // temps sed below only
  V3F tVF = new V3F();

  class V2F extends Point2D.Float {

    V2F( float x, float y )     { super( x,   y   ); }
    V2F( int   x, int   y )     { super( x,   y   ); }
    V2F( V2F u )                { super( u.x, u.y ); }
    V2F()                       { super(          ); }

    V2F zero()                  { x  = 0.f;       y  = 0.f;       return this; }
    V2F set( float z, float w ) { x  = z;         y  = w;         return this; }
    V2F set( int   z, int   w ) { x  = z;         y  = w;         return this; }
    V2F setPlot( V3F p, V2I c ) { x  = p.xP(c);   y  = p.yP(c);   return this; } // p proj rel to center c pixels
    V2F copy(    V2F    u )     { x  = u.x;       y  = u.y;       return this; }
    V2F copy(    V2I    u )     { x  = u.x;       y  = u.y;       return this; }
    V2F copy(    Point  u )     { x  = u.x;       y  = u.y;       return this; }
    V2F plus(    V2F    u )     { x += u.x;       y += u.y;       return this; }
    V2F plus(    V2I    u )     { x += u.x;       y += u.y;       return this; }
    V2F plus(    Point  u )     { x += u.x;       y += u.y;       return this; }
    V2F minus(   V2F    u )     { x -= u.x;       y -= u.y;       return this; }
    V2F minus(   V2I    u )     { x -= u.x;       y -= u.y;       return this; }
    V2F minus(   Point  u )     { x -= u.x;       y -= u.y;       return this; }
    V2F times(   float  f )     { x *= f;         y *= f;         return this; }
    V2F flip()                  {                 y  = -y;        return this; } // Reverse y-component sign
    V2F rot()                   { float t=x; x=y; y=-t;           return this; } // 90 degrees clockwise
    V2F norm()                  { return this.times( 1.f/this.mag() ); }

    V2F setDir(  double a )     {
      x  = (float)Math.sin(a);                                                   // Angle zero is north.
      y  = (float)Math.cos(a);
      return this;                                                               // unit direction vector
    }

    V2F setProj( V3F    p )     {
      float f  = 1.f/( 1.f - kOverViewDist3.dot(p) );
      tVF.copy(p).minus(eye3D);
      x = f * iO.dot(tVF);
      y = f * jO.dot(tVF);
      return this;
    }

    float mag()                 { return (float)Math.sqrt( (double)( x*x + y*y ) ); }
    float sqMag()               { return x*x   + y*y;   }
    float cross(  V2F   u )     { return x*u.y - y*u.x; }                        // not a vector
    float dot(    V2F   u )     { return x*u.x + y*u.y; }
    float sqDist( V2F   u )     { return tV2.copy(this).minus(u).sqMag(); }
    float sqDist( V2I   u )     { return tV2.copy(this).minus(u).sqMag(); }
    float sqDist( Point u )     { return tV2.copy(this).minus(u).sqMag(); }
    float dist(   V2F   u )     { return tV2.copy(this).minus(u).mag();   }
    float dist(   V2I   u )     { return tV2.copy(this).minus(u).mag();   }
    float dist(   Point u )     { return tV2.copy(this).minus(u).mag();   }

    public String toString() {
      String s = new String("(");
      return s.concat( java.lang.Float.toString(x) ).concat(", ")
              .concat( java.lang.Float.toString(y) ).concat(") ");
    }
  }

  /* 2D line objects derived from V2Fs. */
  class L2F extends Line2D.Float {
    L2F()                   { super();                       }
    L2F(     V2F p, V2F q ) { super(   p.x, p.y, q.x, q.y ); }
    L2F set( V2F p, V2F q ) { setLine( p.x, p.y, q.x, q.y ); return this; }

    public String toString() {
      String s = new String("( ");
      V2F p = new V2F( x1, y1 );
      V2F q = new V2F( x2, y2 );
      return s.concat( p.toString() + q.toString() + ")" );
    }
  }

  /* An extension of Point similar to V2F above except it uses Integer coordinates instead of Floats. */

  V2I tP  = new V2I();                                     // Temporary location used below only.

  class V2I extends Point {

    V2I()                       { super();           }
    V2I( Point p )              { super( p );        }
    V2I( V2I   p )              { super( p.x, p.y ); }
    V2I( int x, int y )         { super(   x,   y ); }

    V2I set( int   u, int   v ) { x  =               u;  y  =            v;  return this; }
    V2I set( float u, float v ) { x  =          (int)u;  y  =       (int)v;  return this; }
    V2I setPlot( V3F p, V2I c ) { x  =    (int)p.xP(c);  y  = (int)p.yP(c);  return this; }
    V2I copy(    V2I   p  )     { x  =            p.x ;  y  =         p.y ;  return this; }
    V2I copy(    Point p  )     { x  =            p.x ;  y  =         p.y ;  return this; }
    V2I copy(    V2F   p  )     { x  =      (int)(p.x);  y  =   (int)(p.y);  return this; }
    V2I plus(    V2I   p  )     { x +=            p.x ;  y +=         p.y ;  return this; }
    V2I plus(    Point p  )     { x +=            p.x ;  y +=         p.y ;  return this; }
    V2I plus(    V2F   p  )     { x +=      (int)(p.x);  y +=   (int)(p.y);  return this; }
    V2I minus(   V2I   p  )     { x -=            p.x ;  y -=         p.y ;  return this; }
    V2I minus(   Point p  )     { x -=            p.x ;  y -=         p.y ;  return this; }
    V2I minus(   V2F   p  )     { x -=      (int)(p.x);  y -=   (int)(p.y);  return this; }
    V2I flip()                  {                        y  =           -y;  return this; }

    int   sqMag()               { return x*x + y*y; }
    float mag()                 { return (float)Math.sqrt( (double)( x*x + y*y ) ); }
    int   sqDist( V2I p )       { return tP.copy(this).minus(p).sqMag(); }
    float dist(   V2I p )       { return (float)Math.sqrt( (double)this.sqDist(p) ); }

    public String toString() {
      String s = new String("(");
      return s.concat( Integer.toString(x) ).concat(", ")
              .concat( Integer.toString(y) ).concat(") ");
    }
  }

  /* Comment on temporary point variables:  Rather than unnecessarily propagate garbage in frequently
     executing functions, there are a large number of point objects allocated at top level and
     used locally.  The names have the form tIdd for V2Is, tFdd for V2Fs, and tVdd for the V3Fs.  E.g.,
     tI21 or tV42.  The number of such point objects is not important, but it is important that none
     of these be used in a function called by another which uses the same named temp.  Thus the
     temporary point objects are declared in groups with a consistent value for the first "d" digit.
     Such a group of temps is used only in the code immediately following the declarations.  In many
     cases, a variable with a meaningful name or short length is set to refer to the temporary point
     object.  The code would be simpler if I could just do a "new PointObject" every time I needed
     a temporary variable; but I fear this is inefficient because I have no confidence that the
     compiler can figure out that these objects can be allocated on the stack, in which case they
     would not create garbage needing collection.  So, if someone can assure me that I am not giving
     the compiler enough credit, please do so. */
}
