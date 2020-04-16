package com.hackforsweden.touchmenot

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView

class ScannedDeviceListAdapter (private val context: Context,
                                private val dataList: ArrayList<DeviceListItem>) : BaseAdapter() {

    private val inflater: LayoutInflater = this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    override fun getCount(): Int { return dataList.size }
    override fun getItem(position: Int): Int { return position }
    override fun getItemId(position: Int): Long { return position.toLong() }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var dataitem = dataList[position]

        val rowView = inflater.inflate(R.layout.device_row, parent, false)
        rowView.findViewById<TextView>(R.id.tvDeviceName).text = dataitem.deviceName

        if(dataitem.showRemove){
            rowView.findViewById<Button>(R.id.remove).visibility = View.VISIBLE
            rowView.findViewById<Button>(R.id.Add).visibility = View.GONE

        }else{
            rowView.findViewById<Button>(R.id.remove).visibility = View.GONE

        }

        rowView.findViewById<Button>(R.id.Add).setOnClickListener{
            DbHelper.getInstance(context).addDeviceId(dataitem.address,dataitem.deviceName)
            rowView.findViewById<TextView>(R.id.tvAdded) .visibility = View.VISIBLE

        }

        rowView.findViewById<Button>(R.id.remove).setOnClickListener{

            DbHelper.getInstance(context).deleteDeviceId(dataitem.address)

            dataList.removeAt(position)
            this.notifyDataSetChanged()
        }


        rowView.tag = position
        return rowView
    }


}