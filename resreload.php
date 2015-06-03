<?php
$res_dir="app/src/main/res/";
$google_icon_dir="/sdk/android-icons/material-design-icons-1.0.1/";
$dat_white=$dat_black=$dat="";
if ($dh = opendir("{$res_dir}drawable-mdpi/")) {
	while (($file = readdir($dh)) !== false) {
		if (preg_match('/^ic_(.*)_grey600_36dp\.png$/m', $file, $matches)) {
			$orig_file=$file;
			echo $matches[1] . ": ";
			$dh2=opendir($google_icon_dir);
			while(($d2=readdir($dh2))!==false){
				if(file_exists("$google_icon_dir/$d2/drawable-mdpi/$file")){
					echo "$d2\n";
					$dat.="\t<attr name=\"ic_{$d2}_{$matches[1]}\" format=\"reference\" />\r\n";
					$file_white=str_replace("_grey600", "_white", $file);
					$file_black=str_replace("_grey600", "_black", $file);
					foreach(explode(",","mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi") as $dpi){
						copy("{$google_icon_dir}$d2/drawable-{$dpi}/$file", "{$res_dir}drawable-{$dpi}/$file");
						copy("{$google_icon_dir}$d2/drawable-{$dpi}/$file_white", "{$res_dir}drawable-{$dpi}/$file_white");
						copy("{$google_icon_dir}$d2/drawable-{$dpi}/$file_black", "{$res_dir}drawable-{$dpi}/$file_black");
					}
					$dat_white.="\t\t<item name=\"ic_{$d2}_{$matches[1]}\">@drawable/".substr($file_white,0,-4)."</item>\r\n";
					$dat_black.="\t\t<item name=\"ic_{$d2}_{$matches[1]}\">@drawable/".substr($file_black,0,-4)."</item>\r\n";
					break;
				}
			}
		}
	}
	closedir($dh);
}
file_put_contents("{$res_dir}values/attr_icon.xml", '<?xml version="1.0" encoding="utf-8"?>'."\r\n<resources>\r\n$dat</resources>");
file_put_contents("{$res_dir}values/styles_icon.xml", '<?xml version="1.0" encoding="utf-8"?>
<resources>
	<style name="WithIcon.NoActionBar" parent="Theme.AppCompat.NoActionBar">
'.$dat_white.'
	</style>
	<style name="WithIcon.NoActionBar.Light" parent="Theme.AppCompat.NoActionBar">
'.$dat_black.'
	</style>
</resources>');