package org.estar.tea;


import ngat.phase2.*;
import java.util.*;
import ngat.util.*;

public class DisplaySeq {

	public static String display(int size, ISequenceComponent seq) {

		StringBuffer buff = new StringBuffer();

		if (seq instanceof XExecutiveComponent) {
			XExecutiveComponent exec = (XExecutiveComponent) seq;
			IExecutiveAction action = exec.getExecutiveAction();
			// System.err.println(tab(size*5)+exec.getComponentName()+" ("+action.getClass().getName()+")");
			buff.append("\n" + tab(size * 5) + (exec != null ? exec.getComponentName() : "NULL_EXEC") + " ("
					+ (action != null ? action.getClass().getName() : "NULL_ACTION") + ")");

		} else if (seq instanceof XBranchComponent) {
			XBranchComponent bran = (XBranchComponent) seq;
			List branches = bran.listChildComponents();
			Iterator ib = branches.iterator();
			while (ib.hasNext()) {
				ISequenceComponent bc = (ISequenceComponent) ib.next();
				// System.err.print(tab(size*5)+"BRANCH: "+bc.getComponentName());
				buff.append("\n" + tab(size * 5) + "BRANCH: " + bc.getComponentName());
				buff.append("\n" + display(size + 1, bc));
			}
		} else if (seq instanceof XIteratorComponent) {
			XIteratorComponent iter = (XIteratorComponent) seq;
			XIteratorRepeatCountCondition cond = (XIteratorRepeatCountCondition) iter.getCondition();
			if (cond.getCount() == 1) {
				// System.err.println(tab(size*5)+iter.getComponentName()+" {");
				buff.append("\n" + tab(size * 5) + iter.getComponentName() + " {");
			} else {
				// System.err.println(tab(size*5)+iter.getComponentName()+" x "+cond.getCount()+" {");
				buff.append("\n" + tab(size * 5) + iter.getComponentName() + " x " + cond.getCount() + " {");
			}
			List comps = iter.listChildComponents();
			Iterator ib = comps.iterator();
			while (ib.hasNext()) {
				ISequenceComponent bc = (ISequenceComponent) ib.next();
				buff.append("\n" + display(size + 1, bc));
			}
			// System.err.println(tab(size*5)+"}");
			buff.append("\n" + tab(size * 5) + "}");
		}

		return buff.toString();

	}

	public static String cmd(ISequenceComponent seq) {
		StringBuffer buff = new StringBuffer();
		if (seq instanceof XExecutiveComponent) {
			XExecutiveComponent exec = (XExecutiveComponent) seq;
			IExecutiveAction action = exec.getExecutiveAction();

			if (action instanceof XTarget) {
				buff.append("\nGOTO " + exec.getComponentName());
			} else if (action instanceof XInstrumentConfig) {
				buff.append("\nCONFIG " + ((XInstrumentConfig) action).getInstrumentName() + " "
						+ exec.getComponentName());
			} else {
				buff.append("\n" + (exec != null ? exec.getComponentName() : "NULL_EXEC") + " ("
						+ (action != null ? action.getClass().getName() : "NULL_ACTION") + ")");
			}

		} else if (seq instanceof XBranchComponent) {
			XBranchComponent bran = (XBranchComponent) seq;
			List branches = bran.listChildComponents();
			Iterator ib = branches.iterator();
			while (ib.hasNext()) {
				ISequenceComponent bc = (ISequenceComponent) ib.next();
				buff.append("\n" + cmd(bc));
			}
		} else if (seq instanceof XIteratorComponent) {
			XIteratorComponent iter = (XIteratorComponent) seq;
			XIteratorRepeatCountCondition cond = (XIteratorRepeatCountCondition) iter.getCondition();
			if (cond.getCount() == 1) {
				buff.append("\n" + iter.getComponentName() + " {");
			} else {
				buff.append("\n" + iter.getComponentName() + " x " + cond.getCount() + " {");
			}
			List comps = iter.listChildComponents();
			Iterator ib = comps.iterator();
			while (ib.hasNext()) {
				ISequenceComponent bc = (ISequenceComponent) ib.next();
				buff.append("\n" + cmd(bc));
			}
			buff.append("\n}");
		}

		return buff.toString();

	}

	private static String tab(int size) {
		StringBuffer st = new StringBuffer();
		for (int i = 0; i < size; i++) {
			st.append(" ");
		}
		return st.toString();
	}

}