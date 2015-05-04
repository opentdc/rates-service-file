/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Arbalo AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.opentdc.rates.file;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.opentdc.file.AbstractFileServiceProvider;
import org.opentdc.rates.RatesModel;
import org.opentdc.rates.ServiceProvider;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.NotFoundException;

public class FileServiceProvider extends AbstractFileServiceProvider<RatesModel> implements ServiceProvider {
	
	private static Map<String, RatesModel> index = null;
	private static final Logger logger = Logger.getLogger(FileServiceProvider.class.getName());

	public FileServiceProvider(
		ServletContext context, 
		String prefix
	) throws IOException {
		super(context, prefix);
		if (index == null) {
			index = new HashMap<String, RatesModel>();
			List<RatesModel> _rates = importJson();
			for (RatesModel _rate : _rates) {
				index.put(_rate.getId(), _rate);
			}
		}
	}

	@Override
	public ArrayList<RatesModel> list(
		String queryType,
		String query,
		long position,
		long size
	) {
		logger.info("list() -> " + count() + " values");
		return new ArrayList<RatesModel>(index.values());
	}

	@Override
	public RatesModel create(RatesModel rate) throws DuplicateException {
		logger.info("create(" + rate + ")");
		String _id = rate.getId();
		if (_id != null && _id != "" && index.get(rate.getId()) != null) {
			// object with same ID exists already
			throw new DuplicateException();
		}
		RatesModel _rate = new RatesModel(rate.getTitle(), rate.getRate(),
				rate.getDescription());
		index.put(_rate.getId(), _rate);
		if (isPersistent) {
			exportJson(index.values());
		}
		return _rate;
	}

	@Override
	public RatesModel read(String id) throws NotFoundException {
		RatesModel _rate = index.get(id);
		if (_rate == null) {
			throw new NotFoundException("no rate with ID <" + id
					+ "> was found.");
		}
		logger.info("read(" + id + ") -> " + _rate);
		return _rate;
	}

	@Override
	public RatesModel update(
		String id, 
		RatesModel rate
	) throws NotFoundException {
		if(index.get(id) == null) {
			throw new NotFoundException();
		} else {
			index.put(rate.getId(), rate);
			logger.info("update(" + rate + ")");
			if (isPersistent) {
				exportJson(index.values());
			}
			return rate;
		}
	}

	@Override
	public void delete(String id) throws NotFoundException {
		RatesModel _rate = index.get(id);
		;
		if (_rate == null) {
			throw new NotFoundException("delete(" + id
					+ "): no such rate was found.");
		}
		index.remove(id);
		logger.info("delete(" + id + ")");
		if (isPersistent) {
			exportJson(index.values());
		}
	}

	@Override
	public int count() {
		int _count = -1;
		if (index != null) {
			_count = index.values().size();
		}
		logger.info("count() = " + _count);
		return _count;
	}
}
