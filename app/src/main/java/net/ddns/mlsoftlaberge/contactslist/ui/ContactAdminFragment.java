/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.ddns.mlsoftlaberge.contactslist.ui;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Photo;
import android.provider.ContactsContract.Data;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.ddns.mlsoftlaberge.contactslist.BuildConfig;
import net.ddns.mlsoftlaberge.contactslist.R;
import net.ddns.mlsoftlaberge.contactslist.util.ImageLoader;
import net.ddns.mlsoftlaberge.contactslist.util.Utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.Date;
import java.util.Vector;

import static java.text.DateFormat.*;

/**
 * This fragment displays admins of a specific contact from the contacts provider. It shows the
 * contact's display photo, name and all its mailing addresses. You can also modify this fragment
 * to show other information, such as phone numbers, email addresses and so forth.
 * <p/>
 * This fragment appears full-screen in an activity on devices with small screen sizes, and as
 * part of a two-pane layout on devices with larger screens, alongside the
 * {@link ContactsListFragment}.
 * <p/>
 * To create an instance of this fragment, use the factory method
 * {@link ContactAdminFragment#newInstance(android.net.Uri)}, passing as an argument the contact
 * Uri for the contact you want to display.
 */
public class ContactAdminFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor> {

    public static final String EXTRA_CONTACT_URI =
            "net.ddns.mlsoftlaberge.contactslist.ui.EXTRA_CONTACT_URI";

    // Defines a tag for identifying log entries
    private static final String TAG = "ContactAdminFragment";

    // The geo Uri scheme prefix, used with Intent.ACTION_VIEW to form a geographical address
    // intent that will trigger available apps to handle viewing a location (such as Maps)
    private static final String GEO_URI_SCHEME_PREFIX = "geo:0,0?q=";

    private Uri mContactUri; // Stores the contact Uri for this fragment instance
    private ImageLoader mImageLoader; // Handles loading the contact image in a background thread

    // Used to store references to key views, layouts and menu items as these need to be updated
    // in multiple methods throughout this class.
    private MenuItem mModifContactMenuItem;

    private ImageView mImageView;
    private TextView mContactName;

    private TextView mContactAddress;
    private ImageButton mMapAddressButton;

    private TextView mContactPhone;
    private TextView mContactEmail;

    private Button mPhoneAdminButton;

    private Button mEmailAdminButton;

    private ImageButton mAddAdminButton;

    private GridView mTransactionGrid;

    private TextView mMemoItem;
    private ImageButton mMemoEditButton;

    private LinearLayout mAddressLayout;

    private LinearLayout mPhoneLayout;

    private LinearLayout mEmailLayout;

    private LinearLayout mNotesLayout;

    private TextView mReformattedItem;

    /**
     * Factory method to generate a new instance of the fragment given a contact Uri. A factory
     * method is preferable to simply using the constructor as it handles creating the bundle and
     * setting the bundle as an argument.
     *
     * @param contactUri The contact Uri to load
     * @return A new instance of {@link ContactAdminFragment}
     */
    public static ContactAdminFragment newInstance(Uri contactUri) {
        // Create new instance of this fragment
        final ContactAdminFragment fragment = new ContactAdminFragment();

        // Create and populate the args bundle
        final Bundle args = new Bundle();
        args.putParcelable(EXTRA_CONTACT_URI, contactUri);

        // Assign the args bundle to the new fragment
        fragment.setArguments(args);

        // Return fragment
        return fragment;
    }

    /**
     * Fragments require an empty constructor.
     */
    public ContactAdminFragment() {
    }

    /**
     * Sets the contact that this Fragment displays, or clears the display if the contact argument
     * is null. This will re-initialize all the views and start the queries to the system contacts
     * provider to populate the contact information.
     *
     * @param contactLookupUri The contact lookup Uri to load and display in this fragment. Passing
     *                         null is valid and the fragment will display a message that no
     *                         contact is currently selected instead.
     */
    public void setContact(Uri contactLookupUri) {

        // In version 3.0 and later, stores the provided contact lookup Uri in a class field. This
        // Uri is then used at various points in this class to map to the provided contact.
        if (Utils.hasHoneycomb()) {
            mContactUri = contactLookupUri;
        } else {
            // For versions earlier than Android 3.0, stores a contact Uri that's constructed from
            // contactLookupUri. Later on, the resulting Uri is combined with
            // Contacts.Data.CONTENT_DIRECTORY to map to the provided contact. It's done
            // differently for these earlier versions because Contacts.Data.CONTENT_DIRECTORY works
            // differently for Android versions before 3.0.
            mContactUri = Contacts.lookupContact(getActivity().getContentResolver(),
                    contactLookupUri);
        }

        // If the Uri contains data, load the contact's image and load contact admins.
        if (contactLookupUri != null) {
            // Asynchronously loads the contact image
            mImageLoader.loadImage(mContactUri, mImageView);

            // Shows the contact photo ImageView and hides the empty view
            mImageView.setVisibility(View.VISIBLE);

            // Shows the edit contact action/menu item
            if (mModifContactMenuItem != null) {
                mModifContactMenuItem.setVisible(true);
            }

            // Starts two queries to to retrieve contact information from the Contacts Provider.
            // restartLoader() is used instead of initLoader() as this method may be called
            // multiple times.
            getLoaderManager().restartLoader(ContactDetailQuery.QUERY_ID, null, this);
            getLoaderManager().restartLoader(ContactAddressQuery.QUERY_ID, null, this);
            getLoaderManager().restartLoader(ContactNotesQuery.QUERY_ID, null, this);
            getLoaderManager().restartLoader(ContactPhoneQuery.QUERY_ID, null, this);
            getLoaderManager().restartLoader(ContactEmailQuery.QUERY_ID, null, this);
        } else {
            // If contactLookupUri is null, then the method was called when no contact was selected
            // in the contacts list. This should only happen in a two-pane layout when the user
            // hasn't yet selected a contact. Don't display an image for the contact, and don't
            // account for the view's space in the layout. Turn on the TextView that appears when
            // the layout is empty, and set the contact name to the empty string. Turn off any menu
            // items that are visible.
            mImageView.setVisibility(View.GONE);
            if (mContactName != null) {
                mContactName.setText("");
            }
            if (mContactAddress != null) {
                mContactAddress.setText("");
            }
            if (mContactPhone != null) {
                mContactPhone.setText("");
            }
            if (mContactEmail != null) {
                mContactEmail.setText("");
            }
            if (mModifContactMenuItem != null) {
                mModifContactMenuItem.setVisible(false);
            }
        }
    }

    /**
     * When the Fragment is first created, this callback is invoked. It initializes some key
     * class fields.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Let this fragment contribute menu items
        setHasOptionsMenu(true);

        /*
         * The ImageLoader takes care of loading and resizing images asynchronously into the
         * ImageView. More thorough sample code demonstrating background image loading as well as
         * admins on how it works can be found in the following Android Training class:
         * http://developer.android.com/training/displaying-bitmaps/
         */
        mImageLoader = new ImageLoader(getActivity(), getLargestScreenDimension()) {
            @Override
            protected Bitmap processBitmap(Object data) {
                // This gets called in a background thread and passed the data from
                // ImageLoader.loadImage().
                return loadContactPhoto((Uri) data, getImageSize());

            }
        };

        // Set a placeholder loading image for the image loader
        mImageLoader.setLoadingImage(R.drawable.ic_contact_picture_180_holo_light);

        // Tell the image loader to set the image directly when it's finished loading
        // rather than fading in
        mImageLoader.setImageFadeIn(false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflates the main layout to be used by this fragment
        final View adminView =
                inflater.inflate(R.layout.contact_admin_fragment, container, false);

        // Gets handles to view objects in the layout
        mImageView = (ImageView) adminView.findViewById(R.id.contact_image);

        mContactName = (TextView) adminView.findViewById(R.id.contact_name);
        mContactName.setVisibility(View.VISIBLE);

        mContactAddress = (TextView) adminView.findViewById(R.id.contact_address);
        mContactAddress.setVisibility(View.VISIBLE);

        // Defines an onClickListener object for the add-admin button
        mMapAddressButton = (ImageButton) adminView.findViewById(R.id.button_map_address);
        // Defines an onClickListener object for the address button
        mMapAddressButton.setOnClickListener(new View.OnClickListener() {
            // Defines what to do when users click the address button
            @Override
            public void onClick(View view) {

                final Intent viewIntent =
                        new Intent(Intent.ACTION_VIEW, constructGeoUri(mContactAddress.getText().toString()));

                // A PackageManager instance is needed to verify that there's a default app
                // that handles ACTION_VIEW and a geo Uri.
                final PackageManager packageManager = getActivity().getPackageManager();

                // Checks for an activity that can handle this intent. Preferred in this
                // case over Intent.createChooser() as it will still let the user choose
                // a default (or use a previously set default) for geo Uris.
                if (packageManager.resolveActivity(
                        viewIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    startActivity(viewIntent);
                } else {
                    // If no default is found, displays a message that no activity can handle
                    // the view button.
                    Toast.makeText(getActivity(),
                            R.string.no_intent_found, Toast.LENGTH_SHORT).show();
                }
            }
        });


        mContactPhone = (TextView) adminView.findViewById(R.id.contact_phone);
        mContactPhone.setVisibility(View.VISIBLE);

        // Defines an onClickListener object for the add-admin button
        mPhoneAdminButton = (Button) adminView.findViewById(R.id.button_phone);
        mPhoneAdminButton.setOnClickListener(new View.OnClickListener() {
            // Defines what to do when users click the address button
            @Override
            public void onClick(View view) {
                // Displays a message that no activity can handle the view button.
                Toast.makeText(getActivity(), "Phone Admin", Toast.LENGTH_SHORT).show();
            }
        });

        mContactEmail = (TextView) adminView.findViewById(R.id.contact_email);
        mContactEmail.setVisibility(View.VISIBLE);

        // Defines an onClickListener object for the add-admin button
        mEmailAdminButton = (Button) adminView.findViewById(R.id.button_email);
        mEmailAdminButton.setOnClickListener(new View.OnClickListener() {
            // Defines what to do when users click the address button
            @Override
            public void onClick(View view) {
                // Displays a message that no activity can handle the view button.
                Toast.makeText(getActivity(), "Email Admin", Toast.LENGTH_SHORT).show();
            }
        });


        // Defines an onClickListener object for the add-admin button
        mAddAdminButton = (ImageButton) adminView.findViewById(R.id.button_add_admin);
        mAddAdminButton.setOnClickListener(new View.OnClickListener() {
            // Defines what to do when users click the address button
            @Override
            public void onClick(View view) {
                // Displays a message that no activity can handle the view button.
                Toast.makeText(getActivity(), "Add Admin", Toast.LENGTH_SHORT).show();
            }
        });


        mTransactionGrid = (GridView) adminView.findViewById(R.id.transaction_grid);
        // mTransactionGrid.setAdapter(new TransactionAdapter());

        mMemoItem = (TextView) adminView.findViewById(R.id.contact_memo_item);
        // Defines an onClickListener object for the add-admin button
        mMemoEditButton = (ImageButton) adminView.findViewById(R.id.button_edit_memo);
        mMemoEditButton.setOnClickListener(new View.OnClickListener() {
            // Defines what to do when users click the address button
            @Override
            public void onClick(View view) {
                // Displays a message that no activity can handle the view button.
                Toast.makeText(getActivity(), "Edit Notes", Toast.LENGTH_SHORT).show();
            }
        });


        mAddressLayout = (LinearLayout) adminView.findViewById(R.id.contact_address_layout);

        mNotesLayout = (LinearLayout) adminView.findViewById(R.id.contact_notes_layout);

        mPhoneLayout = (LinearLayout) adminView.findViewById(R.id.contact_phone_layout);

        mEmailLayout = (LinearLayout) adminView.findViewById(R.id.contact_email_layout);

        mReformattedItem = (TextView) adminView.findViewById(R.id.contact_reformatted_item);

        return adminView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // If not being created from a previous state
        if (savedInstanceState == null) {
            // Sets the argument extra as the currently displayed contact
            setContact(getArguments() != null ?
                    (Uri) getArguments().getParcelable(EXTRA_CONTACT_URI) : null);
        } else {
            // If being recreated from a saved state, sets the contact from the incoming
            // savedInstanceState Bundle
            setContact((Uri) savedInstanceState.getParcelable(EXTRA_CONTACT_URI));
        }
    }

    /**
     * When the Fragment is being saved in order to change activity state, save the
     * currently-selected contact.
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Saves the contact Uri
        outState.putParcelable(EXTRA_CONTACT_URI, mContactUri);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // When "edit" menu option selected
            case R.id.menu_modif_contact:
                // Standard system edit contact intent
                Intent intent = new Intent(Intent.ACTION_EDIT, mContactUri);

                // Because of an issue in Android 4.0 (API level 14), clicking Done or Back in the
                // People app doesn't return the user to your app; instead, it displays the People
                // app's contact list. A workaround, introduced in Android 4.0.3 (API level 15) is
                // to set a special flag in the extended data for the Intent you send to the People
                // app. The issue is does not appear in versions prior to Android 4.0. You can use
                // the flag with any version of the People app; if the workaround isn't needed,
                // the flag is ignored.
                intent.putExtra("finishActivityOnSaveCompleted", true);

                // Start the edit activity
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        // Inflates the options menu for this fragment
        inflater.inflate(R.menu.contact_admin_menu, menu);

        // Gets a handle to the "find" menu item
        mModifContactMenuItem = menu.findItem(R.id.menu_modif_contact);

        // If contactUri is null the edit menu item should be hidden, otherwise
        // it is visible.
        mModifContactMenuItem.setVisible(mContactUri != null);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            // Two main queries to load the required information
            case ContactDetailQuery.QUERY_ID:
                // This query loads main contact admins, see
                // ContactDetailQuery for more information.
                return new CursorLoader(getActivity(), mContactUri,
                        ContactDetailQuery.PROJECTION,
                        null, null, null);
            case ContactAddressQuery.QUERY_ID:
                // This query loads contact address admins, see
                // ContactAddressQuery for more information.
                final Uri uri = Uri.withAppendedPath(mContactUri, Contacts.Data.CONTENT_DIRECTORY);
                return new CursorLoader(getActivity(), uri,
                        ContactAddressQuery.PROJECTION,
                        ContactAddressQuery.SELECTION,
                        null, null);
            case ContactNotesQuery.QUERY_ID:
                // This query loads contact address admins, see
                // ContactAddressQuery for more information.
                final Uri nuri = Uri.withAppendedPath(mContactUri, Contacts.Data.CONTENT_DIRECTORY);
                return new CursorLoader(getActivity(), nuri,
                        ContactNotesQuery.PROJECTION,
                        ContactNotesQuery.SELECTION,
                        null, null);
            case ContactPhoneQuery.QUERY_ID:
                // This query loads contact address admins, see
                // ContactAddressQuery for more information.
                final Uri puri = Uri.withAppendedPath(mContactUri, Contacts.Data.CONTENT_DIRECTORY);
                return new CursorLoader(getActivity(), puri,
                        ContactPhoneQuery.PROJECTION,
                        ContactPhoneQuery.SELECTION,
                        null, null);
            case ContactEmailQuery.QUERY_ID:
                // This query loads contact address admins, see
                // ContactAddressQuery for more information.
                final Uri euri = Uri.withAppendedPath(mContactUri, Contacts.Data.CONTENT_DIRECTORY);
                return new CursorLoader(getActivity(), euri,
                        ContactEmailQuery.PROJECTION,
                        ContactEmailQuery.SELECTION,
                        null, null);
        }
        return null;
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // Nothing to do here. The Cursor does not need to be released as it was never directly
        // bound to anything (like an adapter).
    }


    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

        // If this fragment was cleared while the query was running
        // eg. from from a call like setContact(uri) then don't do
        // anything.
        if (mContactUri == null) {
            return;
        }

        // Each LinearLayout has the same LayoutParams so this can
        // be created once and used for each cumulative layouts of data
        final LinearLayout.LayoutParams layoutParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);


        switch (loader.getId()) {
            case ContactDetailQuery.QUERY_ID:
                // Moves to the first row in the Cursor
                if (data.moveToFirst()) {
                    // For the contact admins query, fetches the contact display name.
                    // ContactDetailQuery.DISPLAY_NAME maps to the appropriate display
                    // name field based on OS version.
                    final String contactName = data.getString(ContactDetailQuery.DISPLAY_NAME);
                    if (mContactName != null) {
                        mContactName.setText(contactName);
                    }
                    // In the single pane layout, sets the activity title
                    // to the contact name. On HC+ this will be set as
                    // the ActionBar title text.
                    getActivity().setTitle(contactName);

                }
                break;
            case ContactAddressQuery.QUERY_ID:
                // Clears out the details layout first in case the details
                // layout has addresses from a previous data load still
                // added as children.
                mAddressLayout.removeAllViews();
                // This query loads the contact address .
                // Loops through all the rows in the Cursor
                if (data.moveToFirst()) {
                    // Fills the address field with the first address
                    mContactAddress.setText(data.getString(ContactAddressQuery.ADDRESS));
                    // loop thru all addresses for the bottom layout
                    do {
                        // Builds the address layout
                        final LinearLayout alayout = buildAddressLayout(
                                data.getInt(ContactAddressQuery.TYPE),
                                data.getString(ContactAddressQuery.LABEL),
                                data.getString(ContactAddressQuery.ADDRESS));
                        // Adds the new address layout to the addresses layout
                        mAddressLayout.addView(alayout, layoutParams);
                    } while (data.moveToNext());
                } else {
                    // If nothing found, clear the address field
                    mContactAddress.setText("");
                }
                break;
            case ContactPhoneQuery.QUERY_ID:
                // Clears out the details layout first in case the details
                // layout has data from a previous data load still
                // added as children.
                mPhoneLayout.removeAllViews();
                // This query loads the contact phone
                // Loops through all the rows in the Cursor
                if (data.moveToFirst()) {
                    // Fills the phone field with the primary phone
                    mContactPhone.setText(data.getString(ContactPhoneQuery.PHONE));
                    // loop thru all phones for the bottom layout
                    do {
                        final LinearLayout playout = buildPhoneLayout(
                                data.getInt(ContactPhoneQuery.TYPE),
                                data.getString(ContactPhoneQuery.LABEL),
                                data.getString(ContactPhoneQuery.PHONE));
                        // Adds the new phone layout to the phones layout
                        mPhoneLayout.addView(playout, layoutParams);
                    } while (data.moveToNext());
                } else {
                    // If nothing found, clear the phone field
                    mContactPhone.setText("");
                }
                break;
            case ContactEmailQuery.QUERY_ID:
                // Clears out the details layout first in case the details
                // layout has data from a previous data load still
                // added as children.
                mEmailLayout.removeAllViews();
                // This query loads the contact email
                // Loops through all the rows in the Cursor
                if (data.moveToFirst()) {
                    // Fills the email field with primary email
                    mContactEmail.setText(data.getString(ContactEmailQuery.EMAIL));
                    // loop thru all emails for the bottom layout
                    do {
                        final LinearLayout elayout = buildEmailLayout(
                                data.getInt(ContactEmailQuery.TYPE),
                                data.getString(ContactEmailQuery.LABEL),
                                data.getString(ContactEmailQuery.EMAIL));
                        // Adds the new address layout to the details layout
                        mEmailLayout.addView(elayout, layoutParams);
                        // store full note, and process it
                    } while (data.moveToNext());
                } else {
                    // If nothing found, adds an empty address layout
                    mContactEmail.setText("");
                }
                break;
            case ContactNotesQuery.QUERY_ID:
                // Clears out the details layout first in case the details
                // layout has data from a previous data load still
                // added as children.
                mNotesLayout.removeAllViews();
                // This query loads the contact notes
                // Get the first row of the cursor (table contains only one row)
                if (data.moveToFirst()) {
                    // store full note, and process it
                    mNotesData = data.getString(ContactNotesQuery.NOTE);
                    expandnote();
                    compactnote();
                    // loop thru all notes for the bottom layout (only one)
                    do {
                        // Builds the notes layout
                        final LinearLayout nlayout = buildNotesLayout(
                                data.getString(ContactNotesQuery.NOTE));
                        // Adds the new note layout to the notes layout
                        mNotesLayout.addView(nlayout, layoutParams);
                    } while (data.moveToNext());
                } else {
                    // If nothing found, clear the data
                    mNotesData = "";
                    clearnote();
                }
                // display the memo part of the note in the memo field (decoded note)
                mMemoItem.setText(notememo);
                // initialise the adapter for the grid of transactions to fill
                mTransactionGrid.setAdapter(new TransactionAdapter());
                break;
        }
    }

    // --------------------------------------------------------------------
    // this is the decoding/encoding part of the transaction table
    // --------------------------------------------------------------------
    // feed string user by the note decoder
    private String mNotesData = "";

    // string containing the memo part of the note
    private StringBuffer notememo = new StringBuffer();

    // string containing the reformatted part of the note
    private StringBuffer notereformat = new StringBuffer();

    // temporary space for line in treatment
    private String noteline = "";

    // structure of a row of the table
    private class Transac extends Object {
        double qte;
        double prix;
        double mnt;
        Date fdate;
        String trdate;
        String amount;
        String descrip;
    }

    // tables containing the expanded note data
    private Vector<Transac> transaclist = new Vector<Transac>(10, 10);

    // one row of the table
    private Transac transac;

    // counter of table row
    private int nbtransac = 0;

    // clear the table of fields for an empty note
    private void clearnote() {
        nbtransac = 0;
        notememo.setLength(0);
        transaclist.removeAllElements();
    }

    // expand the note in a table of fields
    // scan mNotesData and cut it in fields and tables
    private void expandnote() {
        clearnote();

        // expand note in fields
        int i = 0;
        int p;
        while (i < mNotesData.length()) {
            // find the position of the end of line (may be end of block too)
            p = mNotesData.indexOf('\n', i);
            if (p < 0) {
                p = mNotesData.length() - 1;
            }
            noteline = mNotesData.substring(i, p + 1);
            // if an admin line, then decode it
            // else add it to the memo field
            if (noteline.indexOf("ADMIN|") == 0) {
                decodeline();
            } else {
                notememo.append(noteline);
            }
            // advance after the last char eated by line
            i = p + 1;
        }
    }

    // decode the noteline beginning by ADMIN| and add it to the table
    private void decodeline() {
        // create an empty default transaction
        transac = new Transac();
        transac.qte = 1.0;
        transac.prix = 0.0;
        transac.mnt = 0.0;
        transac.trdate = "20160101";
        transac.amount = "0.00";
        transac.descrip = noteline;
        // decode the line
        int i = 6;
        int p;
        // search for the date
        p = noteline.indexOf('|', i);
        if (p < 0) p = noteline.length() - 1;
        if (p >= i) {
            transac.trdate = noteline.substring(i, p);
            i = p + 1;
            // search for the amount
            p = noteline.indexOf('|', i);
            if (p < 0) p = noteline.length() - 1;
            if (p >= i) {
                transac.amount = noteline.substring(i, p);
                i = p + 1;
                // search for the description
                p = noteline.indexOf('|', i);
                if (p < 0) p = noteline.indexOf('\n', i);
                if (p < 0) p = noteline.length();
                if (p >= i) {
                    transac.descrip = noteline.substring(i, p);
                    i = p + 1;
                }
            }
        }
        // reformat the amount
        transac.mnt = Double.valueOf(transac.amount);
        transac.prix = transac.mnt;
        transac.amount = String.format("%.2f", transac.mnt);
        // reformat the date
        transac.fdate = stringToDate(transac.trdate);
        transac.trdate = dateToString(transac.fdate);
        // add element to the table
        transaclist.addElement(transac);
        nbtransac++;
    }

    // transform the transaction table in a memo form, and display it in the last bottom debug field
    private void compactnote() {
        int i;
        notereformat.setLength(0);
        // refill the string with the transactions list in readable mode
        for(i=0;i<nbtransac;++i) {
            transac = transaclist.elementAt(i);
            notereformat.append("ADMIN|");
            notereformat.append(transac.trdate);
            notereformat.append("|");
            notereformat.append(transac.amount);
            notereformat.append("|");
            notereformat.append(transac.descrip);
            notereformat.append("\n");
        }
        mReformattedItem.setText(notereformat);
    }

    // convert a string to a date field
    private Date stringToDate(String sdate) {
        // prepare the date parser.
        Calendar cal = Calendar.getInstance();
        int year, month, day, hour, min, sec;
        String syear, smonth, sday, shour, smin, ssec;
        syear = sdate.substring(0, 4);
        smonth = sdate.substring(4, 6);
        sday = sdate.substring(6, 8);
        year = Integer.valueOf(syear);
        month = Integer.valueOf(smonth);
        day = Integer.valueOf(sday);
        hour = 0;
        min = 0;
        sec = 0;
        cal.set(year, month - 1, day, hour, min, sec);
        long ldate = cal.getTimeInMillis();
        return new Date(ldate);
    }

    // convert a date field to a readable string
    private String dateToString(Date date) {
        // prepare the date parser.
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int year, month, day, hour, min, sec;
        year = cal.get(Calendar.YEAR);
        month = cal.get(Calendar.MONTH) + 1;
        day = cal.get(Calendar.DAY_OF_MONTH);
        hour = cal.get(Calendar.HOUR_OF_DAY);
        min = cal.get(Calendar.MINUTE);
        sec = cal.get(Calendar.SECOND);
        return String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, min, sec);
    }

    // return a view for every cell of the grid table
    public class TransactionAdapter extends BaseAdapter {
        public TransactionAdapter() {
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout layout;

            if (convertView == null) {
                // Inflates the address layout
                layout = (LinearLayout) LayoutInflater.from(getActivity()).inflate(
                        R.layout.contact_transaction_item, null, false);
            } else {
                layout = (LinearLayout) convertView;
            }

            TextView t = (TextView) layout.findViewById(R.id.contact_transaction_description);
            TextView dt = (TextView) layout.findViewById(R.id.contact_transaction_date);
            TextView mnt = (TextView) layout.findViewById(R.id.contact_transaction_amount);

            transac = transaclist.elementAt(position);

            t.setText(transac.descrip);
            dt.setText(transac.trdate);
            mnt.setText(transac.amount);

            return layout;
        }


        public final int getCount() {
            return nbtransac;
        }

        public final Object getItem(int position) {
            return transaclist.elementAt(position);
        }

        public final long getItemId(int position) {
            return position;
        }
    }

    /**
     * Builds a notes LinearLayout based on note information from the Contacts Provider.
     * Each note for the contact gets its own LinearLayout object; for example, if the contact
     * has three notes, then 3 LinearLayouts are generated.
     *
     * @param note From
     *             {@link android.provider.ContactsContract.CommonDataKinds.Note#NOTE}
     * @return A LinearLayout to add to the contact notes layout,
     * populated with the provided notes.
     */
    private LinearLayout buildNotesLayout(final String note) {

        // Inflates the address layout
        final LinearLayout notesLayout =
                (LinearLayout) LayoutInflater.from(getActivity()).inflate(
                        R.layout.contact_notes_item, mNotesLayout, false);

        // Gets handles to the view objects in the layout
        final TextView nheaderTextView =
                (TextView) notesLayout.findViewById(R.id.contact_notes_header);
        final TextView notesTextView =
                (TextView) notesLayout.findViewById(R.id.contact_notes_item);
        final ImageButton editNotesButton =
                (ImageButton) notesLayout.findViewById(R.id.button_edit_notes);

        // If there's no addresses for the contact, shows the empty view and message, and hides the
        // header and button.
        if (note == null) {
            nheaderTextView.setVisibility(View.GONE);
            editNotesButton.setVisibility(View.GONE);
            notesTextView.setText(R.string.no_notes);
        } else {
            // Sets TextView objects in the layout
            nheaderTextView.setText("Note");
            notesTextView.setText(note);

            // Defines an onClickListener object for the address button
            editNotesButton.setOnClickListener(new View.OnClickListener() {
                // Defines what to do when users click the address button
                @Override
                public void onClick(View view) {
                    // Displays a message that no activity can handle the view button.
                    Toast.makeText(getActivity(), "Edit Note", Toast.LENGTH_SHORT).show();
                }
            });

        }
        return notesLayout;
    }


    /**
     * Builds a notes LinearLayout based on note information from the Contacts Provider.
     * Each note for the contact gets its own LinearLayout object; for example, if the contact
     * has three notes, then 3 LinearLayouts are generated.
     *
     * @param type      From
     *                         {@link android.provider.ContactsContract.CommonDataKinds.Phone#TYPE}
     * @param label From
     *                         {@link android.provider.ContactsContract.CommonDataKinds.Phone#LABEL}
     * @param phone From
     *                         {@link android.provider.ContactsContract.CommonDataKinds.Phone#NUMBER}
     * @return A LinearLayout to add to the contact notes layout,
     * populated with the provided notes.
     */
    private LinearLayout buildPhoneLayout(final int type, final String label, final String phone) {

        // Inflates the address layout
        final LinearLayout phoneLayout =
                (LinearLayout) LayoutInflater.from(getActivity()).inflate(
                        R.layout.contact_phone_item, mPhoneLayout, false);

        // Gets handles to the view objects in the layout
        final TextView pheaderTextView =
                (TextView) phoneLayout.findViewById(R.id.contact_phone_header);
        final TextView phoneTextView =
                (TextView) phoneLayout.findViewById(R.id.contact_phone_item);
        final ImageButton editPhoneButton =
                (ImageButton) phoneLayout.findViewById(R.id.button_edit_phone);

        // If there's no addresses for the contact, shows the empty view and message, and hides the
        // header and button.
        if (phone == null) {
            pheaderTextView.setVisibility(View.GONE);
            editPhoneButton.setVisibility(View.GONE);
            phoneTextView.setText("");
        } else {
            // Gets postal address label type
            CharSequence plabel =
                    android.provider.ContactsContract.CommonDataKinds.Phone.getTypeLabel(getResources(), type, label);

            // Sets TextView objects in the layout
            pheaderTextView.setText(plabel + " Phone");
            phoneTextView.setText(phone);

            // Defines an onClickListener object for the address button
            editPhoneButton.setOnClickListener(new View.OnClickListener() {
                // Defines what to do when users click the address button
                @Override
                public void onClick(View view) {
                    // Displays a message that no activity can handle the view button.
                    Toast.makeText(getActivity(), "Edit Phone", Toast.LENGTH_SHORT).show();
                }
            });

        }
        return phoneLayout;
    }


    /**
     * Builds a notes LinearLayout based on note information from the Contacts Provider.
     * Each note for the contact gets its own LinearLayout object; for example, if the contact
     * has three notes, then 3 LinearLayouts are generated.
     *
     * @param type      From
     *                         {@link android.provider.ContactsContract.CommonDataKinds.Email#TYPE}
     * @param label From
     *                         {@link android.provider.ContactsContract.CommonDataKinds.Email#LABEL}
     * @param email From
     *                         {@link android.provider.ContactsContract.CommonDataKinds.Email#ADDRESS}
     * @return A LinearLayout to add to the contact notes layout,
     * populated with the provided notes.
     */
    private LinearLayout buildEmailLayout(final int type, final String label, final String email) {

        // Inflates the address layout
        final LinearLayout emailLayout =
                (LinearLayout) LayoutInflater.from(getActivity()).inflate(
                        R.layout.contact_email_item, mEmailLayout, false);

        // Gets handles to the view objects in the layout
        final TextView eheaderTextView =
                (TextView) emailLayout.findViewById(R.id.contact_email_header);
        final TextView emailTextView =
                (TextView) emailLayout.findViewById(R.id.contact_email_item);
        final ImageButton editEmailButton =
                (ImageButton) emailLayout.findViewById(R.id.button_edit_email);

        // If there's no addresses for the contact, shows the empty view and message, and hides the
        // header and button.
        if (email == null) {
            eheaderTextView.setVisibility(View.GONE);
            editEmailButton.setVisibility(View.GONE);
            emailTextView.setText("");
        } else {
            // Gets postal address label type
            CharSequence elabel =
                    android.provider.ContactsContract.CommonDataKinds.Email.getTypeLabel(getResources(), type, label);

            // Sets TextView objects in the layout
            eheaderTextView.setText(elabel + " Email");
            emailTextView.setText(email);

            // Defines an onClickListener object for the address button
            editEmailButton.setOnClickListener(new View.OnClickListener() {
                // Defines what to do when users click the address button
                @Override
                public void onClick(View view) {
                    // Displays a message that no activity can handle the view button.
                    Toast.makeText(getActivity(), "Edit Email", Toast.LENGTH_SHORT).show();
                }
            });

        }
        return emailLayout;
    }


    /**
     * Builds an address LinearLayout based on address information from the Contacts Provider.
     * Each address for the contact gets its own LinearLayout object; for example, if the contact
     * has three postal addresses, then 3 LinearLayouts are generated.
     *
     * @param addressType      From
     *                         {@link android.provider.ContactsContract.CommonDataKinds.StructuredPostal#TYPE}
     * @param addressTypeLabel From
     *                         {@link android.provider.ContactsContract.CommonDataKinds.StructuredPostal#LABEL}
     * @param address          From
     *                         {@link android.provider.ContactsContract.CommonDataKinds.StructuredPostal#FORMATTED_ADDRESS}
     * @return A LinearLayout to add to the contact details layout,
     * populated with the provided address details.
     */
    private LinearLayout buildAddressLayout(int addressType, String addressTypeLabel,
                                            final String address) {

        // Inflates the address layout
        final LinearLayout addressLayout =
                (LinearLayout) LayoutInflater.from(getActivity()).inflate(
                        R.layout.contact_address_item, mAddressLayout, false);

        // Gets handles to the view objects in the layout
        final TextView headerTextView =
                (TextView) addressLayout.findViewById(R.id.contact_address_header);
        final TextView addressTextView =
                (TextView) addressLayout.findViewById(R.id.contact_address_full);
        final ImageButton viewAddressButton =
                (ImageButton) addressLayout.findViewById(R.id.button_view_address);

        // If there's no addresses for the contact, shows the empty view and message, and hides the
        // header and button.
        if (addressTypeLabel == null && addressType == 0) {
            headerTextView.setVisibility(View.GONE);
            viewAddressButton.setVisibility(View.GONE);
            addressTextView.setText(R.string.no_address);
        } else {
            // Gets postal address label type
            CharSequence label =
                    StructuredPostal.getTypeLabel(getResources(), addressType, addressTypeLabel);

            // Sets TextView objects in the layout
            headerTextView.setText(label + " Address");
            addressTextView.setText(address);

            // Defines an onClickListener object for the address button
            viewAddressButton.setOnClickListener(new View.OnClickListener() {
                // Defines what to do when users click the address button
                @Override
                public void onClick(View view) {

                    final Intent viewIntent =
                            new Intent(Intent.ACTION_VIEW, constructGeoUri(address));

                    // A PackageManager instance is needed to verify that there's a default app
                    // that handles ACTION_VIEW and a geo Uri.
                    final PackageManager packageManager = getActivity().getPackageManager();

                    // Checks for an activity that can handle this intent. Preferred in this
                    // case over Intent.createChooser() as it will still let the user choose
                    // a default (or use a previously set default) for geo Uris.
                    if (packageManager.resolveActivity(
                            viewIntent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                        // Toast.makeText(getActivity(),
                        //        R.string.yes_intent_found, Toast.LENGTH_SHORT).show();
                        startActivity(viewIntent);
                    } else {
                        // If no default is found, displays a message that no activity can handle
                        // the view button.
                        Toast.makeText(getActivity(),
                                R.string.no_intent_found, Toast.LENGTH_SHORT).show();
                    }
                }
            });

        }
        return addressLayout;
    }


    /**
     * Constructs a geo scheme Uri from a postal address.
     *
     * @param postalAddress A postal address.
     * @return the geo:// Uri for the postal address.
     */
    private Uri constructGeoUri(String postalAddress) {
        // Concatenates the geo:// prefix to the postal address. The postal address must be
        // converted to Uri format and encoded for special characters.
        return Uri.parse(GEO_URI_SCHEME_PREFIX + Uri.encode(postalAddress));
    }

    /**
     * Fetches the width or height of the screen in pixels, whichever is larger. This is used to
     * set a maximum size limit on the contact photo that is retrieved from the Contacts Provider.
     * This limit prevents the app from trying to decode and load an image that is much larger than
     * the available screen area.
     *
     * @return The largest screen dimension in pixels.
     */
    private int getLargestScreenDimension() {
        // Gets a DisplayMetrics object, which is used to retrieve the display's pixel height and
        // width
        final DisplayMetrics displayMetrics = new DisplayMetrics();

        // Retrieves a displayMetrics object for the device's default display
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        final int height = displayMetrics.heightPixels;
        final int width = displayMetrics.widthPixels;

        // Returns the larger of the two values
        return height > width ? height : width;
    }

    /**
     * Decodes and returns the contact's thumbnail image.
     *
     * @param contactUri The Uri of the contact containing the image.
     * @param imageSize  The desired target width and height of the output image in pixels.
     * @return If a thumbnail image exists for the contact, a Bitmap image, otherwise null.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private Bitmap loadContactPhoto(Uri contactUri, int imageSize) {

        // Ensures the Fragment is still added to an activity. As this method is called in a
        // background thread, there's the possibility the Fragment is no longer attached and
        // added to an activity. If so, no need to spend resources loading the contact photo.
        if (!isAdded() || getActivity() == null) {
            return null;
        }

        // Instantiates a ContentResolver for retrieving the Uri of the image
        final ContentResolver contentResolver = getActivity().getContentResolver();

        // Instantiates an AssetFileDescriptor. Given a content Uri pointing to an image file, the
        // ContentResolver can return an AssetFileDescriptor for the file.
        AssetFileDescriptor afd = null;

        if (Utils.hasICS()) {
            // On platforms running Android 4.0 (API version 14) and later, a high resolution image
            // is available from Photo.DISPLAY_PHOTO.
            try {
                // Constructs the content Uri for the image
                Uri displayImageUri = Uri.withAppendedPath(contactUri, Photo.DISPLAY_PHOTO);

                // Retrieves an AssetFileDescriptor from the Contacts Provider, using the
                // constructed Uri
                afd = contentResolver.openAssetFileDescriptor(displayImageUri, "r");
                // If the file exists
                if (afd != null) {
                    // Reads and decodes the file to a Bitmap and scales it to the desired size
                    return ImageLoader.decodeSampledBitmapFromDescriptor(
                            afd.getFileDescriptor(), imageSize, imageSize);
                }
            } catch (FileNotFoundException e) {
                // Catches file not found exceptions
                if (BuildConfig.DEBUG) {
                    // Log debug message, this is not an error message as this exception is thrown
                    // when a contact is legitimately missing a contact photo (which will be quite
                    // frequently in a long contacts list).
                    Log.d(TAG, "Contact photo not found for contact " + contactUri.toString()
                            + ": " + e.toString());
                }
            } finally {
                // Once the decode is complete, this closes the file. You must do this each time
                // you access an AssetFileDescriptor; otherwise, every image load you do will open
                // a new descriptor.
                if (afd != null) {
                    try {
                        afd.close();
                    } catch (IOException e) {
                        // Closing a file descriptor might cause an IOException if the file is
                        // already closed. Nothing extra is needed to handle this.
                    }
                }
            }
        }

        // If the platform version is less than Android 4.0 (API Level 14), use the only available
        // image URI, which points to a normal-sized image.
        try {
            // Constructs the image Uri from the contact Uri and the directory twig from the
            // Contacts.Photo table
            Uri imageUri = Uri.withAppendedPath(contactUri, Photo.CONTENT_DIRECTORY);

            // Retrieves an AssetFileDescriptor from the Contacts Provider, using the constructed
            // Uri
            afd = getActivity().getContentResolver().openAssetFileDescriptor(imageUri, "r");

            // If the file exists
            if (afd != null) {
                // Reads the image from the file, decodes it, and scales it to the available screen
                // area
                return ImageLoader.decodeSampledBitmapFromDescriptor(
                        afd.getFileDescriptor(), imageSize, imageSize);
            }
        } catch (FileNotFoundException e) {
            // Catches file not found exceptions
            if (BuildConfig.DEBUG) {
                // Log debug message, this is not an error message as this exception is thrown
                // when a contact is legitimately missing a contact photo (which will be quite
                // frequently in a long contacts list).
                Log.d(TAG, "Contact photo not found for contact " + contactUri.toString()
                        + ": " + e.toString());
            }
        } finally {
            // Once the decode is complete, this closes the file. You must do this each time you
            // access an AssetFileDescriptor; otherwise, every image load you do will open a new
            // descriptor.
            if (afd != null) {
                try {
                    afd.close();
                } catch (IOException e) {
                    // Closing a file descriptor might cause an IOException if the file is
                    // already closed. Ignore this.
                }
            }
        }

        // If none of the case selectors match, returns null.
        return null;
    }

    /**
     * This interface defines constants used by contact retrieval queries.
     */
    public interface ContactDetailQuery {
        // A unique query ID to distinguish queries being run by the
        // LoaderManager.
        final static int QUERY_ID = 1;

        // The query projection (columns to fetch from the provider)
        @SuppressLint("InlinedApi")
        final static String[] PROJECTION = {
                Contacts._ID,
                Utils.hasHoneycomb() ? Contacts.DISPLAY_NAME_PRIMARY : Contacts.DISPLAY_NAME,
                Contacts.STARRED,
                Contacts.LOOKUP_KEY,
                Contacts.PHOTO_URI,
        };

        // The query column numbers which map to each value in the projection
        final static int ID = 0;
        final static int DISPLAY_NAME = 1;
        final static int STARRED = 2;
        final static int LOOKUP_KEY = 3;
        final static int PHOTO_URI = 4;
    }

    /**
     * This interface defines constants used by address retrieval queries.
     */
    public interface ContactAddressQuery {
        // A unique query ID to distinguish queries being run by the
        // LoaderManager.
        final static int QUERY_ID = 2;

        // The query projection (columns to fetch from the provider)
        final static String[] PROJECTION = {
                StructuredPostal._ID,
                StructuredPostal.FORMATTED_ADDRESS,
                StructuredPostal.TYPE,
                StructuredPostal.LABEL,
        };

        // The query selection criteria. In this case matching against the
        // StructuredPostal content mime type.
        final static String SELECTION =
                Data.MIMETYPE + "='" + StructuredPostal.CONTENT_ITEM_TYPE + "'";

        // The query column numbers which map to each value in the projection
        final static int ID = 0;
        final static int ADDRESS = 1;
        final static int TYPE = 2;
        final static int LABEL = 3;
    }

    /**
     * This interface defines constants used by address retrieval queries.
     */
    public interface ContactNotesQuery {
        // A unique query ID to distinguish queries being run by the
        // LoaderManager.
        final static int QUERY_ID = 3;

        // The query projection (columns to fetch from the provider)
        final static String[] PROJECTION = {
                ContactsContract.CommonDataKinds.Note._ID,
                ContactsContract.CommonDataKinds.Note.NOTE,
        };

        // The query selection criteria. In this case matching against the
        // Note content mime type.
        final static String SELECTION =
                Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE + "'";

        // The query column numbers which map to each value in the projection
        final static int ID = 0;
        final static int NOTE = 1;
    }

    /**
     * This interface defines constants used by address retrieval queries.
     */
    public interface ContactPhoneQuery {
        // A unique query ID to distinguish queries being run by the
        // LoaderManager.
        final static int QUERY_ID = 4;

        // The query projection (columns to fetch from the provider)
        final static String[] PROJECTION = {
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
        };

        // The query selection criteria. In this case matching against the
        // Note content mime type.
        final static String SELECTION =
                Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'";

        // The query column numbers which map to each value in the projection
        final static int ID = 0;
        final static int PHONE = 1;
        final static int TYPE = 2;
        final static int LABEL = 3;
    }

    /**
     * This interface defines constants used by address retrieval queries.
     */
    public interface ContactEmailQuery {
        // A unique query ID to distinguish queries being run by the
        // LoaderManager.
        final static int QUERY_ID = 5;

        // The query projection (columns to fetch from the provider)
        final static String[] PROJECTION = {
                ContactsContract.CommonDataKinds.Email._ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.TYPE,
                ContactsContract.CommonDataKinds.Email.LABEL,
        };

        // The query selection criteria. In this case matching against the
        // Note content mime type.
        final static String SELECTION =
                Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE + "'";

        // The query column numbers which map to each value in the projection
        final static int ID = 0;
        final static int EMAIL = 1;
        final static int TYPE = 2;
        final static int LABEL = 3;
    }


}
