# Japagoge (APNG-GIF screen grabber)

[![License Apache 2.0](https://img.shields.io/badge/license-Apache%20License%202.0-green.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Java 11+](https://img.shields.io/badge/java-11%2b-green.svg)](https://bell-sw.com/pages/downloads/#/java-11-lts)
[![PayPal donation](https://img.shields.io/badge/donation-PayPal-cyan.svg)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=AHWJHJFBAWGL2)
[![YooMoney donation](https://img.shields.io/badge/donation-Yoo.money-blue.svg)](https://yoomoney.ru/to/41001158080699)

## Changelog

- 2.0.0 (11-sep-2021)
  - improved rendering for mouse pointer
  - added support of GIF export
  - added support of color filters recording

- __1.0.1 (01-sep-2021)__
  - internal optimizations to decrease result file size

- __1.0.0 (30-aug-2021)__
  - initial release

## Pre-word

During writing an article for some online resource, I wanted to make small GIF animations and expected to use Peek
utility for that. I was badly surprised to detect that the utility doesn't work well in multi-monitor environment.
Googling didn't help me find any simple working analogue, so I decided quickly make some similar application but
implemented in Java for cross-platform use. By default, it grabs into APNG images (Animation PNG records) abd it allows
save TrueColor (in opposite to Gif which supports only 256 colors). But since 2.0.0 version, conversion into GIF
allowed, by default it uses AUTO palette based on image statistics. There are some number of predefined palettes which
are selectable through preferences.

## How to use the utility?

You can download its distributive for your OS from [the releases page](https://github.com/raydac/japagoge/releases). It
requires Java 11+ for work but some archives provide bundled JRE. Keep in mind that it is very tricky to grab mouse
pointer appearance under Java so grabbed pointer look may differ from your system one.

### Positioning

Just after start, the window in positioning mode and shown in green color. All communications with the window only by
mouse and you can drag the window and resize it to cover required screen area. Only in the positioning mode, you can see
two buttons in the right top corner of the application window. The left one calls the options dialog and the right one
closes the application. Keep in mind that click the close buttons is the only way to close the application.      
![Positioning state](assets/screens/state_positioning.png)

### Recording

__Recording activation started only through mouse double-click on the window title area__. In recording mode all buttons
will be removed and the window changes its color.   
![Positioning state](assets/screens/state_recording.png)   
__To stop recording, you should again make mouse double-click on the window title area.__ After stop of recording you
will see the save file dialog to save your recorded file, if you press cancel button then the record will be ignored and
just deleted. I have not implemented any optimization for saved data so that result files can be big. If you choose GIF
file as the result one then conversion will be started, it can take some time and depends on power of your computer.

### Options

You can tune recording options. For instance disable show of mouse pointer or limit number of loops in the result record
show. For that you should click the preferences button during positioning mode, so you will see the Preferences dialog.
If you are going to make GIF file as the result one then I would recommend make some sample with different palettes to
find better result. Flag `Accurate RGB' can increase precision of colors during conversion but increasing spent
time.    
![Positioning state](assets/screens/state_preferences.png)

### F.A.Q.

#### How to convert APNG into GIF?

If you work under Ubuntu then you can use `apng2gif` utility to convert file format.

### I have unexpected color artifacts in result GIF

Try change `Palette for RGB to GIF` from AUTO to UNIVERSAL or any other fixed palette, also try turn on
flag `Accurate RGB`.