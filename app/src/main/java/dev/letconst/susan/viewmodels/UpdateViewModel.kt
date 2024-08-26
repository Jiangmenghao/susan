package dev.letconst.susan.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dev.letconst.susan.utils.UpdateManager

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val updateManager: UpdateManager = UpdateManager(application)

    private val _updateAvailable = MutableLiveData<Boolean>()
    val updateAvailable: LiveData<Boolean> get() = _updateAvailable

    private val _updateVersion = MutableLiveData<String?>()
    val updateVersion: LiveData<String?> get() = _updateVersion

    private val _updateUrl = MutableLiveData<String?>()
    private val updateUrl: LiveData<String?> get() = _updateUrl

    private val _updateDescription = MutableLiveData<String?>()
    val updateDescription: LiveData<String?> get() = _updateDescription

    private val _downloadProgress = MutableLiveData<Int>()
    val downloadProgress: LiveData<Int> = _downloadProgress

    var updateDialogShown = false
        private set

    fun checkForUpdates() {
        updateManager.checkForUpdates { isUpdateAvailable, url, description, updateVersion ->
            _updateUrl.postValue(url)
            _updateDescription.postValue(description)
            _updateVersion.postValue(updateVersion)
            _updateAvailable.postValue(isUpdateAvailable)
        }
    }

    fun downloadAndInstallApk() {
        updateUrl.value?.let {
            updateManager.downloadAndInstallApk(it) { progress ->
                _downloadProgress.postValue(progress)
            }
        }
    }

    fun setUpdateDialogShown(shown: Boolean) {
        updateDialogShown = shown
    }
}