package cc.martix.drop;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;

import cc.martix.drop.pages.receive.ReceiveFragment;
import cc.martix.drop.pages.history.HistoryFragment;
import cc.martix.drop.pages.home.HomeFragment;
import cc.martix.drop.pages.send.SendFragment;
import cc.martix.drop.utils.PermissionUtils;
import cc.martix.drop.utils.ToastUtils;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private MaterialToolbar mToolbar;
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private Fragment mReceiveFragment;
    private Fragment mHistoryFragment;
    private Fragment mSendFragment;

    private Fragment mCurrentFragment = null;
    private FragmentManager mFragmentManager;
    private Fragment mHomeFragment;

    private final int ACCEPT_PERMISSIONS_REQUEST_CODE = 1;

    public static final String PUBSUB_KEY_SWITCH_FRAGMENT = "MainActivity.switchFragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFragmentManager = getSupportFragmentManager();
        /* fragments */
        mHomeFragment = new HomeFragment();
        mReceiveFragment = new ReceiveFragment();
        mHistoryFragment = new HistoryFragment();
        mSendFragment = new SendFragment();

        mToolbar = findViewById(R.id.tool_bar);
        setSupportActionBar(mToolbar);

        mDrawerLayout = findViewById(R.id.drawer_layout);
        assert mDrawerLayout != null;
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, mDrawerLayout, mToolbar, R.string.open_drawer, R.string.close_drawer);
        mDrawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        mNavigationView = findViewById(R.id.navigation_view);
        assert mNavigationView != null;
        mNavigationView.setNavigationItemSelectedListener(this);

        /* 设置主页 */
        switchFragment(SWITCH_TO_HOME);
        /* 订阅 */
        PubSub.instance.subscribe(PUBSUB_KEY_SWITCH_FRAGMENT, this::switchFragment);
    }


    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    /* 跳转导航 next */
    public static final int SWITCH_TO_HOME = 1;
    public static final int SWITCH_TO_SEND = 2;
    public static final int SWITCH_TO_RECEIVE = 3;
    public static final int SWITCH_TO_HISTORY = 4;

    public void switchFragment(int next) {
        mDrawerLayout.closeDrawer(GravityCompat.START);
        Fragment f;
        switch (next) {
            case SWITCH_TO_RECEIVE:
                f = mReceiveFragment;
                break;
            case SWITCH_TO_SEND:
                f = mSendFragment;
                break;
            case SWITCH_TO_HISTORY:
                f = mHistoryFragment;
                break;
            case SWITCH_TO_HOME:
                f = mHomeFragment;
                break;
            default:
                return;
        }
        if (mCurrentFragment == f) return;
        if (mCurrentFragment == null) {
            mFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_left_to_right, R.anim.slide_out_right_to_left)
                    .add(R.id.content_fragment, f)
                    .commitNow();
        } else if (f.isAdded()) {
            mFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_left_to_right, R.anim.slide_out_right_to_left, R.anim.slide_in_left_to_right, R.anim.slide_out_right_to_left)
                    .hide(mCurrentFragment)
                    .show(f)
                    .commitNow();
        } else {
            mFragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_left_to_right, R.anim.slide_out_right_to_left, R.anim.slide_in_left_to_right, R.anim.slide_out_right_to_left)
                    .hide(mCurrentFragment)
                    .add(R.id.content_fragment, f)
                    .commitNow();
        }
        mCurrentFragment = f;
    }

    /*OnNavigationItemSelectedListener*/
    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_home:
                switchFragment(SWITCH_TO_HOME);
                return true;
            case R.id.menu_send:
                switchFragment(SWITCH_TO_SEND);
                return true;
            case R.id.menu_receive:
                if (PermissionUtils.checkIfHasPermissions(this, ReceiveFragment.getRequiredPermissions())) {
                    switchFragment(SWITCH_TO_RECEIVE);
                } else {
                    requestPermissions(ReceiveFragment.getRequiredPermissions(), ACCEPT_PERMISSIONS_REQUEST_CODE);
                }
                return true;
            case R.id.menu_history:
                switchFragment(SWITCH_TO_HISTORY);
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (ACCEPT_PERMISSIONS_REQUEST_CODE == requestCode) {
            if (!PermissionUtils.validatePermissionGrantedResult(grantResults)) {
                ToastUtils.showRequirePermissionToast(this);
            }
        }
    }

}