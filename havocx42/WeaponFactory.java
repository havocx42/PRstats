/*	Copyright 2013 havocx42
	
	This file is part of PRStats.

    PRStats is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    PRStats is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with PRStats.  If not, see <http://www.gnu.org/licenses/>.
*/
package havocx42;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.logging.Logger;

public class WeaponFactory {
	static String					magSize					= "ObjectTemplate.ammo.magSize ";
	static String					reloadTime				= "ObjectTemplate.ammo.reloadTime ";
	static String					recoilForceUp			= "ObjectTemplate.recoil.recoilForceUp CRD_UNIFORM/";
	static String					recoilForceLeftRight	= "ObjectTemplate.recoil.recoilForceLeftRight CRD_UNIFORM/";
	static String					minDev					= "ObjectTemplate.deviation.minDev ";
	static String					setFireDev				= "ObjectTemplate.deviation.setFireDev ";
	static String					setTurnDev				= "ObjectTemplate.deviation.setTurnDev ";
	static String					setSpeedDev				= "ObjectTemplate.deviation.setSpeedDev ";
	static String					setMiscDev				= "ObjectTemplate.deviation.setMiscDev ";
	static String					devModStand				= "ObjectTemplate.deviation.devModStand ";
	static String					devModCrouch			= "ObjectTemplate.deviation.devModCrouch ";
	static String					devModLie				= "ObjectTemplate.deviation.devModLie ";
	static String					devModZoom				= "ObjectTemplate.deviation.devModZoom ";
	static String					projectileTemplate		= "ObjectTemplate.projectileTemplate ";
	static String					velocity				= "ObjectTemplate.velocity ";
	static String					roundsPerMinute			= "ObjectTemplate.fire.roundsPerMinute ";

	private Map<String, Ammunition>	ammos;
	private final static Logger		LOGGER					= Logger.getLogger(WeaponFactory.class.getName());

	public WeaponFactory(Map<String, Ammunition> ammo) {
		ammos = ammo;
	}

	public Weapon createWeaponFromFile(File f, Map<String, Weapon> weapons) {
		String weaponName = f.getName().substring(0, f.getName().indexOf('.'));

		Weapon result = null;
		result = weapons.get(weaponName);
		if (result == null) {
			result = new Weapon();
			LOGGER.finer("Creating weapon: " + weaponName);
		} else {
			LOGGER.finer("Updating weapon: " + weaponName);
		}
		Deviation dev = new Deviation();
		result.dev = dev;
		result.name = weaponName;

		includeFile(result, f, new String[0]);

		return result;

	}

	private void includeFile(Weapon weapon, File f, String[] args) {
		LOGGER.finer("Including file: " + f.getName());
		// System.out.println("Including File: " + f.getName());
		try (BufferedReader reader = new BufferedReader(new FileReader(f))) {

			Boolean executing = true;
			Stack<IfState> ifStack = new Stack<IfState>();
			while (true) {
				String line;
				line = reader.readLine();
				if (line == null) {
					break;
				}

				if (line.trim().startsWith("if ")) {
					if (executing) {
						int arg = Integer.valueOf(line.substring(line.indexOf("v_arg") + 5, line.indexOf("v_arg") + 6));
						Boolean condition = (args[arg - 1].equals(line.substring(line.indexOf("\"") + 1, line.lastIndexOf("\""))));
						ifStack.push(new IfState(executing, condition));
						executing = ifStack.peek().condition && ifStack.peek().executing;
					} else {
						ifStack.push(new IfState(executing, null));
					}
				} else if (line.contains("elseIf ")) {
					int arg = Integer.valueOf(line.substring(line.indexOf("v_arg") + 5, line.indexOf("v_arg") + 6));
					Boolean condition = (args[arg - 1].equals(line.substring(line.indexOf("\"") + 1, line.lastIndexOf("\""))));
					executing = condition && !ifStack.peek().condition && ifStack.peek().executing;
					if (condition) {
						ifStack.peek().condition = true;
					}
				} else if (line.trim().equals("endIf")) {
					executing = ifStack.pop().executing;
				} else if (line.trim().equals("else")) {
					executing = !ifStack.peek().condition && ifStack.peek().executing;
				}
				if (executing) {
					if (line.trim().startsWith("include")) {
						for (int i = 1; i < 10; i++) {
							if (line.contains("v_arg" + i)) {
								line = line.replace("v_arg" + i, "\"" + safeArrayAccess(args, i - 1) + "\"");
							}
						}
						// System.out.println("Include line: " + line);
						int startingIndex = line.indexOf("include") + "include".length();
						int endingIndex = line.indexOf("\"");
						endingIndex = endingIndex < 0 ? line.length() : endingIndex;
						String fileName = line.substring(startingIndex, endingIndex).trim();

						ArrayList<String> newArgs = new ArrayList<String>();
						String arguments = line.substring(endingIndex).trim();
						StringTokenizer st = new StringTokenizer(arguments);
						while (st.hasMoreTokens()) {
							newArgs.add(st.nextToken().replace("\"", ""));
						}

						/*
						 * 
						 * Boolean inside = false; StringBuilder currentArg =
						 * new StringBuilder(); for (int i = line.indexOf(" ");
						 * i < line.length(); i++) { if (line.charAt(i) ==
						 * (' ')) { if (inside) {
						 * newArgs.add(currentArg.toString()); } currentArg =
						 * new StringBuilder(); inside = !inside; } else { if
						 * (line.charAt(i) != '\"')
						 * currentArg.append(line.charAt(i)); } }
						 */
						String[] newArgsArray = new String[newArgs.size()];
						File includeFile = new File(f.getParentFile(), fileName);
						includeFile(weapon, includeFile, newArgs.toArray(newArgsArray));
					} else {
						populateWeaponFromLine(weapon, line);
					}
				}
			}
		} catch (IOException e1) {
			LOGGER.fine("Unable to include file: " + e1.getMessage() + " for Weapon:" + weapon.name);
			return;
		}
	}

	private Object safeArrayAccess(Object[] objects, int i) {
		if (i < objects.length) {
			return objects[i];
		} else {
			return null;
		}
	}

	private void populateWeaponFromLine(Weapon weapon, String line) {
		try {
			if (line.contains(magSize)) {
				weapon.magSize = Double.valueOf(line.substring(line.lastIndexOf(magSize) + magSize.length(), line.length()));

			} else if (line.contains(reloadTime)) {
				weapon.reloadTime = Double.valueOf(line.substring(line.lastIndexOf(reloadTime) + reloadTime.length(), line.length()));

			} else if (line.contains(recoilForceUp)) {
				weapon.recoilForceUp = line.substring(line.lastIndexOf(recoilForceUp) + recoilForceUp.length(), line.length());

			} else if (line.contains(recoilForceLeftRight)) {
				weapon.recoilForceLeftRight = line.substring(line.lastIndexOf(recoilForceLeftRight) + recoilForceLeftRight.length(),
						line.length());

			} else if (line.contains(velocity)) {
				weapon.velocity = Double.valueOf(line.substring(line.lastIndexOf(velocity) + velocity.length(), line.length()));
			}

			else if (line.contains(roundsPerMinute)) {
				weapon.roundsPerMinute = Double.valueOf(line.substring(line.lastIndexOf(roundsPerMinute) + roundsPerMinute.length(),
						line.length()));
			}

			// set deviation

			else if (line.contains(minDev)) {
				weapon.dev.minDev = Double.valueOf(line.substring(line.lastIndexOf(minDev) + minDev.length(), line.length()));
			} else if (line.contains(setFireDev)) {
				String[] vals = line.substring(line.lastIndexOf(setFireDev) + setFireDev.length(), line.length()).split(" |\t");
				weapon.dev.setFireDevMax = Double.valueOf(vals[0]);
				weapon.dev.setFireDevAdd = Double.valueOf(vals[1]);
				weapon.dev.setFireDevCool = Double.valueOf(vals[2]);
			} else if (line.contains(setTurnDev)) {
				String[] vals = line.substring(line.lastIndexOf(setTurnDev) + setTurnDev.length(), line.length()).split(" ");
				weapon.dev.setTurnDevMax = Double.valueOf(vals[0]);
				weapon.dev.setTurnDevLeft = Double.valueOf(vals[1]);
				weapon.dev.setTurnDevRight = Double.valueOf(vals[2]);
				weapon.dev.setTurnDevCool = Double.valueOf(vals[3]);
			} else if (line.contains(setSpeedDev)) {
				String[] vals = line.substring(line.lastIndexOf(setSpeedDev) + setSpeedDev.length(), line.length()).split(" ");
				weapon.dev.setSpeedDevMax = Double.valueOf(vals[0]);
				weapon.dev.setSpeedDevMove = Double.valueOf(vals[1]);
				weapon.dev.setSpeedDevStrafe = Double.valueOf(vals[2]);
				weapon.dev.setSpeedDevCool = Double.valueOf(vals[3]);
			} else if (line.contains(setMiscDev)) {
				String[] vals = line.substring(line.lastIndexOf(setMiscDev) + setMiscDev.length(), line.length()).split(" ");
				weapon.dev.setMiscDevMax = Double.valueOf(vals[0]);
				weapon.dev.setMiscDevAdd = Double.valueOf(vals[1]);
				weapon.dev.setMiscDevCool = Double.valueOf(vals[2]);
			} else if (line.contains(devModStand)) {
				weapon.dev.devModStand = Double
						.valueOf(line.substring(line.lastIndexOf(devModStand) + devModStand.length(), line.length()));
			} else if (line.contains(devModCrouch)) {
				weapon.dev.devModCrouch = Double.valueOf(line.substring(line.lastIndexOf(devModCrouch) + devModCrouch.length(),
						line.length()));
			} else if (line.contains(devModLie)) {
				weapon.dev.devModLie = Double.valueOf(line.substring(line.lastIndexOf(devModLie) + devModLie.length(), line.length()));
			} else if (line.contains(devModZoom)) {
				weapon.dev.devModZoom = Double.valueOf(line.substring(line.lastIndexOf(devModZoom) + devModZoom.length(), line.length()));
			}

			else if (line.contains(projectileTemplate)) {
				Ammunition ammo = ammos.get(line.substring(line.lastIndexOf(projectileTemplate) + projectileTemplate.length(),
						line.length()).toLowerCase());
				if (ammo == null) {
					LOGGER.fine("Unable to find ammunition: "
							+ line.substring(line.lastIndexOf(projectileTemplate) + projectileTemplate.length(), line.length())
									.toLowerCase() + " for weapon:" + weapon.name);
				}
				weapon.ammo = ammo;
			}
		} catch (Exception e) {
			System.err.println(line);
			throw e;
		}
	}
}
